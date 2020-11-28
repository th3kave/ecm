# ECM
## What is it?
ECM stands for Eventually Consistent Microservice.

It is intended for use in building service endpoints with complex execution flows that change the state of numerous data entities in a single operation.

When such operations fail mid-flight, they can leave data in an inconsistent state, from which the service is often unable to recover without manual intervention.

ECM facilitates designing of service endpoints as set of isolated units of work called `Branches`; breaking down complex operations into several simpler steps with clearly demarcated boundaries, each of which can be executed either consecutively or concurrently (some branches are likely to depend on the completion of others, while other branches could run in parallel).

The motivation being that if a branch fails, it does so in isolation, and when a branch completes successfully, it need not be executed again.

This has the following advantages:

 * An operation can complete partially, in which case, data modified by completed branches will be in a consistent state
 * Failed branches can be retried in order to *eventually* complete the operation
 * If the operation is still incomplete after retries, knowing which branches completed successfully and which failed could considerably simplify troubleshooting
 * Operations that interact with multiple resource managers can be made transactional on a per branch basis, with each resource manger interaction in a separate branch, coordinated by a resource-local transaction manager
 * Branches can be tested in isolation

## Example
The following class shows a simple implementation of an `Operation`. An `Operation` represents the work done by a single service endpoint and contains methods annotated by `@Branch` denoting each branch of execution in the operation.

```java
@Component
@RequiredArgsConstructor
public class SpringExampleOperation {

    private final Service service;

    @PostConstruct
    void init() {
        Operation.bindToServcie(service, this);
    }

    // Optional - if not provided, default behaviour is as this method
    @BeforeBranches
    public BranchInput<?> before(OperationContext context) {
        return BranchInput.builder().value(context.getRequest().getPayload()).build();
    }

    // Optional - if not provided, default behaviour is as this method
    @AfterBranches
    public Response after(OperationContext context) {
        return context.responseBuilder().payload(context.getBrancheOutputs()).build();
    }

    // This method will run concurrently with branch2()
    @Branch
    public BranchOutput<String> branch1(BranchContext context) {
        BranchInput<String> input = context.getInput();
        // do stuff
        String result = input.getValue() + "/path/to";
        return context.outputBuilder(String.class)
                .result(result)
                .build();
    }

    // This method will run concurrently with branch1()
    @Branch
    public BranchOutput<String> branch2(BranchContext context) {
        // do stuff
        String result = "file.txt";
        return context.outputBuilder(String.class)
                .result(result)
                .build();
    }

    // This method will run after branch1() and branch2() have completed and will
    // have access to output produced by both methods
    @Branch(dependencies = { "branch1", "branch2" })
    public BranchOutput<File> dependent(BranchContext context) {
        BranchOutput<String> branch1Output = context.getDependency("branch1");
        BranchOutput<String> branch2Output = context.getDependency("branch2");
        try {
            // do stuff
            return context.outputBuilder(File.class)
                    .result(new File(branch1Output.getResult() + "/" + branch2Output.getResult()))
                    .build();
        } catch (ClassCastException e) {
            // will prevent retry
            throw new NonRecoverableBranchException(e);
        } catch (IllegalStateException e) {
            // will retry
            throw e;
        }
    }
}
```
And to call the operation

```java
@Component
@RequiredArgsConstructor
public class SpringExampleClient {

    private final Service service;

    public Response callService() {
    
        String payload = "payload";
        
        Request request = Request.builder()
                .traceId("123")
                .operatonId(SpringExampleOperation.class.getName())
                .payload(payload)
                .build();

        return service.process(request);
    }
}
```
## Operation methods
Branches are created by annotating a method with `@Bracnh` annotation.

A `Branch` can declare dependency on one or more branches, in which case it will run after its dependencies have completed. If, however, it does not declare any dependencies, then the branch runs concurrently with other branches that do not declare dependencies.

Many branches can depend on one branch and one branch can depend on many branches.

Each `Operation` can have one method annotated with `@BeforeBranches` and another with `@AfterBranches`. `BeforeBranches` method is responsible for consuming the `Request` and producing a `BranchInput` that will be made available to all branches. `AfterBranches` method is executed only after **all branches have completed successfully** and, given a `List<BranchOutput<?>>`, is responsible for constructing the `Response` object that is returned to the client.

Both `BeforeBranches` and `AfterBranches` methods execute in a single Thread (e.g. http request Thread) while `Branch` methods execute in separate Threads - even when they run consecutively.

If `BeforeBranches` or `AfterBranches` methods are not provided, the default behaviour is to set the request payload as value of `BranchInput` and to set `List<BranchOutput<?>>` as response payload.

## Errors and retries
If a branch returns an output containing a `BranchError`, the service will attempt retries up to the service `maxTries` property.

A retry attempt will **bypass** `BeforeBranches` method and will only execute **failed branches** and **branches with** `deterministic=false`. If after `maxTries` attempts the response still contains errors, `Service.onErrorAfterRetries(...)` is called and the response is returned to the client. Default implementation of this method logs the failure, but it can be overridden to provide custom behaviour such as send a message or call another service endpoint, etc.

## Threading considerations
`Operation` classes are singletons and should not have mutable state and any components they provide for use by their methods should be thread-safe.

## Contributing
Contributions are what make the open source community such an amazing place to be learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

