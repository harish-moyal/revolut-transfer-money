# revolut-transfer-money
A Java based Funds Transfer Engine with an in-memory DB and remote interface exposed over Rest Services 

* <B>Java Version Used:</B> Java 8
* <B>In-Memory DB:</B> H2
* <B>Web Server:</B> Jetty
* <B>Frameworks Used:</B> No Framework is used, libraries for web server and Rest interface are used.
* <B>Common libraries Used:</B> jersery libraries for supporting REST, jetty library, apache http client for testing REST APIs, H2 DB libraries, jackson for json binding, apache commons lang, apache commons collection.
* <B>Common concerns addressed:</B> Transaction Management at business layer, Concurrency control at persistence layer.

## APIs
* Get All Accounts
* Get Account By Id
* Withdraw Money
* Deposit Money
* Transfer Funds
#### The In-Memory H2 DB is prepopulated with 5 accounts. For simplicity APIs for create and delete account feature have not been provided.

## Architechture of Application
This is a multi layered application with clear separation between individual layers. Following are the layers

1. Database : In-Memory H2 DB
2. Data Access (Persistence) layer
3. Core Business Layer
4. Service Gateway 
5. Application Layer (Rest Services)

Each and every layer can independently be modified and enhanced without affecting any other layer. 

The application flow goes as below:

Rest Client ==> Rest Service ==> Service Gateway ==> Cose Business Service ==> Data Access Layer ==> DB

### Brief Description about each layer

#### Data Access layer

Written in plain JDBC. Refer module ft-persistence-services. This layer can be changed to use any ORM framework or to change DB vendor without affecting any other layer.

#### Core Business Services

##### This contains 
* <B>SDK Bundle</B> with available service interfaces. Services can be annotated with standard javax @Transactional annotation to include transactions with the specified propagation level. Refer module ft-sdk-bundle.
* <B>Core business</B> implementation for the service interfaces. Refer ft-core-services.
* <B>A Service Locator</B> module that locates service interface implementation class to be invoked by service gateway. The purpose of this locator interface is to hide any core implementation level classes to outside layer i.e. service gateway, rest services. This locator has been written in way that adding a new service interface doesn't require any changes here untill and unless we choose to write implementation class not ending with {ServiceInterfaceName}Impl.java or not in the base core impl package i.e. "com.revolut.core.fundstransfer.impl". In case we want to write a Implementation class with different naming convention or outside the base package, we can write a custom Service Locator implementing ServiceLocator.java interface. Refer {AccountServiceLocator.java} However this service locator can be modified to locate service implementation in a more elegant way e.g. using annotation based scanning same as Spring's component scan.
* <B>Transaction Management</B> Module : A module to handle transaction management based on the standard transaction propagation levels. It supports starting and ending transactions with also support for nested transaction. This is thread safe i.e. multiple threads can start or end transactions in parallel without affecting each other. refer module ft-transaction-management
* <B>Connection Manager</B> Module: A module to manage database connections i.e. pooling of connections. As of now connection pooling is not implementated in this project due to time constraint but a clear support is there by adding this module. Adding a connection pool library will only require changes in this module without affecting any other layer.

#### Service Gateway

A Gateway to invoke core business services from any application interface e.g. Rest Interface, SOAP Interface or from any presentation layer application. The purpose of this service gateway to perform common checks and tasks before and after invoking core business services, similar to AOP. In this project i am only putting transaction management in this gateway but this gateway is open to be enhanced for logging, security checks etc.. This calls service locator to get the actual service implementation. Refer module ft-service-gateway


#### Rest Services

An application layer for invoking core business servies through service gateway. This is implemented using JAX-RS specs. This calls service gateway with following inputs:
1. Class type of the Service Interface to be invoked.
2. Method name of the service interface to be invoked.
3. The actual parameters to be passed to invoke the service method. The method params need to be passed in the same order as declared in the methods declaration of the service contract(interface). So that service can invoke the correct method.

Rest interface only have visibility of sdk bundle (service interface and java models) and service gateway. This reduces coupling between app layer and business layer.



## maven command to build the application
```
mvn clean install -DskipTests
```
## command to start funds transfer engine
A separate module called ft-engine has been written to start a standalone restful funds transfer web server on port 7777.
```
sh startFTengine.sh (Linux/MAC) or startFTengine.bat (Windows)
```

## command to run tests
All the integration test cases have been written inside ft-engine module. So go inside ft-engine directory and run below command
```
mvn clean install

Test Cases have been written for AccountService and FundsTransferService.
There are test cases cases written testing concurrency with 1000 threads.
```

## Postman Collection for Rest APIs
```
download revolut-transfer-apis-collection.json from project root directory and import into postman to test the application from postman.
```

A transfer is successfully executed if:
* The source and destination accounts exists
* There is a sufficient balance on the source account
* The currency of the transfer, of the source and destination accounts are identical
* The transfer status is not FAILED or EXECUTED

If one or more of these conditions are not fulfilled, the transfer status is set to FAILED.

