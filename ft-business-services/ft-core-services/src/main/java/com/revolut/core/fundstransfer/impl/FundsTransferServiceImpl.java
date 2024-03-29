package com.revolut.core.fundstransfer.impl;

import com.revolut.core.fundstransfer.gateway.ServicesGateway;
import com.revolut.core.fundstransfer.locks.ObjectsLockManager;
import com.revolut.sdk.fundstransfer.services.AccountService;
import com.revolut.sdk.fundstransfer.services.FundsTransferService;
import com.revolut.sdk.fundstransfer.exception.InternalCoreException;
import com.revolut.sdk.fundstransfer.exception.ServiceException;
import com.revolut.sdk.fundstransfer.exception.ValidationException;
import com.revolut.sdk.fundstransfer.model.AccountVO;
import com.revolut.sdk.fundstransfer.model.TransferRequestVO;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FundsTransferServiceImpl implements FundsTransferService {

    private static Logger logger = Logger.getLogger(FundsTransferServiceImpl.class.getName());
    private final ServicesGateway servicesGateway = ServicesGateway.getServicesGateway();
    private final ObjectsLockManager lockManager = ObjectsLockManager.getInstance();

    @Override
    public void transferFunds(TransferRequestVO transferRequest) throws InternalCoreException, ValidationException {

        if (Objects.isNull(transferRequest.getTransferAmount()) || transferRequest.getTransferAmount().longValue() <= 0) {
            throw new ValidationException("Invalid transfer amount", "303");
        }

        AccountVO sourceAccount = null;
        AccountVO destinationAccount = null;
        try {
            sourceAccount = (AccountVO) servicesGateway.pass(
                    AccountService.class, "getAccount", transferRequest.getSourceAccountId());
            destinationAccount = (AccountVO) servicesGateway.pass(
                    AccountService.class, "getAccount", transferRequest.getDestinationAccountId());
        } catch (ServiceException e) {
            throw new InternalCoreException(e.getMessage(), e.getReasonCode());
        }

        if (sourceAccount.getBalance().longValue() < transferRequest.getTransferAmount().longValue()) {
            throw new ValidationException("Source Account doesn't have sufficient balance", "105");
        }

        int updateCount = 0;
        InternalCoreException exception = null;
        try {
            lockManager.lockMultipleAtomically(sourceAccount.getAccountId(), destinationAccount.getAccountId());
            servicesGateway.pass(
                    AccountService.class, "withdrawFromAccount", sourceAccount.getAccountId(), transferRequest.getTransferAmount());
            updateCount++;
            servicesGateway.pass(
                    AccountService.class, "depositToAccount", destinationAccount.getAccountId(), transferRequest.getTransferAmount());
            updateCount++;
        } catch (ServiceException e) {
            exception = new InternalCoreException("Transfer Failed:" + e.getMessage(), e.getReasonCode());
        } catch (Exception e) {
            exception = new InternalCoreException("Transfer Failed:" + e.getMessage());
        } finally {
            if (updateCount == 1) {
                logger.log(Level.WARNING, "Transfer Failed, Source account was debited successfully, hence crediting back to source account");
                //source account is debited but same is not credited to destination account, hence credit back the amount to source account
                try {
                    servicesGateway.pass(
                            AccountService.class, "depositToAccount", sourceAccount.getAccountId(), transferRequest.getTransferAmount());
                } catch (ServiceException e) {
                    throw new InternalCoreException("Transfer Failed, Error while crediting back to source account:" + e.getMessage(), e.getReasonCode());
                }
            }
            try {
                lockManager.unlockMultiple(sourceAccount.getAccountId(), destinationAccount.getAccountId());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Transfer Failed while unlocking accounts: "+e.getMessage());
                exception = new InternalCoreException("Transfer Failed while unlocking accounts");
            }
            if (exception != null) {
                throw exception;
            }
        }
    }
}
