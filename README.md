# Reactive Programming in Spring - Demo

This Demo repository shows the basic features of reactive programming in Spring. It demonstrates the core concepts and
features of Spring Webflux, Project Reactor and R2DBC.

## Prerequisites

The project needs access to a mongo-db and a postgres db. Both can be run in docker. Just run 
```sh
docker-compose up -d
```
in the project root.

The rest of this documentation assumes that the databases are run in this very way.

> Note: The postgres db is configured with credentials for the user. They are set to:
> `Username: ben` and
> `Password: oop`. These can be changes in `${PROJECT_ROOT}/docker/postgres/Dockerfile`.

## Branches

The demo is structured in four [steps](#Steps). There is a git branch showing the final result of each Step.
The master branch marks the starting point.

## Steps

The slides of the presentaion can be found [here](slides/Reactive%20Spring.pdf).

If you want to start the project from scratch and ignore this repository, go and hit `start.spring.io`. As dependencies
choose:

- Spring Reactive Web
- Spring Data Reactive Mongo Db
- R2DBC
- Postgres SQL Driver

### Steps 1 - Connecting to mongo-db

As a first step, we want to store a simple entity (let's make it a dog) in a mongo db without any blocking operations.
So we go to our [DogsApplication.kt](./backend/src/main/kotlin/com/example/dogs/DogsApplication.kt) and add a data class
for out entity:

```kotlin
@Document
data class Dog(@Id val id: String?, val name: String)
```

Next we need do define a DogsRepository. Normally we would extend our repository interface from `CrudRepository`, but
with spring reactive, we simply extend from `ReactiveCrudRepository`. 

If we look at the signature of this class, we notice that the `save` and `saveAll` method return Mono and Flux types:

```java
Mono<S> save(S entity)
Flux<S> saveAll(Iterable<S> entities)
``` 

A `Mono<S>` means, that a stream of 0 to 1 entities of type `S` will be returned. A `Flux<S>` on the other hand is a 
stream of 0 to n entities.

For now, let's just add our repository:

```kotlin
interface DogsRepository : ReactiveCrudRepository<Dog, String>
```

To see this repository in action, we create a Bean which will save some dogs into our mongo db when the application is
started:

```kotlin
@Component
class InitializeData(
		val dogsRepository: DogsRepository
) : Logging{

	@EventListener(ApplicationReadyEvent::class)
	fun initialize() {
		val saved = Flux.just("Willy", "Wulfi", "Rinona", "Winona")
				.map { name -> Dog(null, name) }
				.flatMap { dog -> this.dogsRepository.save(dog) }

		this.dogsRepository.deleteAll()
				.thenMany(saved )
				.thenMany( this.dogsRepository.findAll() )
				.subscribe(logger::info)
	}
}
```

This code will first delete all entries in the database and then store four dogs.

> Note: No dog is deleted or saved until the `subscribe` method is invoked. This is actually the same with terminal 
> operators in the Java Stream API.

Running the application now should store the dogs in the mongo db. You can verify this be querying the db itself:

1) Run `docker exec -it mongo /bin/bash` to get a shell inside your mongo container.
2) In that shell run `mongo` to connect to the db.
3) Run `use test` to select the test db.
4) Query all dogs with `db.test.find()`

### Step 2 - Connecting to postgres

Now, to do the same with a postgres sql db, we only have to change a few things.

1) In our [build.gradle.kts](./backend/build.gradle.kts) comment out the dependency for
`spring-boot-starter-data-mongodb-reactive`.
2) Comment in the dependencies for`r2dbc-postgresql` and `postgresql`.
3) Refresh (Sync) the gradle build
4) Change the connection settings in [application.properties](backend/src/main/resources/application.properties)
5) Remove the `@Document` annotation from our `Dogs` data class and change the type of the Id to Int:

```kotlin
data class Dog(@Id val id: Int?, val name: String)
```

Running this code again should now save the dogs in the postgres db. You can verify this in the following way:

1) Run `docker exec -it postgres /bin/bash` to get a shell inside your mongo container.
2) In that shell run `psql dogs ben` to connect to the db.
3) Query all dogs with `SELECT * FROM dog;`

### Step 3 - Transactions

Next we want to use transactions and make sure the the data is rolled back, when an error occurs.
We need two additional beans for that:

```kotlin
@Bean
fun reactiveTransactionManager(cf: ConnectionFactory) = R2dbcTransactionManager(cf)

@Bean
fun transactionalOperator(reactiveTransactionManager: ReactiveTransactionManager) = TransactionalOperator.create(reactiveTransactionManager)
```

> Todo: Check if the ConnectionFactory is actually needed, as it seems unused.

With that, we can create a `DogsService` which will save an array of dog names. The Service shall roll the data back,
when a dog is invalid. A dog shall be considered valid if the name starts with a capital letter:

```kotlin
@Service
class DogService(
        val dogsRepository: DogsRepository,
        val transactionalOperator: TransactionalOperator
) {

    private fun isValid(dog: Dog) = Character.isUpperCase(dog.name[0])

    fun saveAll(vararg names: String) = transactionalOperator.transactional(
            Flux.fromArray(names)
                    .map { name -> Dog(null, name) }
                    .flatMap { dog -> this.dogsRepository.save(dog) }
                    .doOnNext { dog -> Assert.isTrue(isValid(dog), "Dog musts start with capital letter") }
    )
}
```

We can now use this Service in our `InitializeData` component:

```kotlin
@Component
class InitializeData(
        val dogsRepository: DogsRepository,
        val dogService: DogService
) : Logging {

    @EventListener(ApplicationReadyEvent::class)
    fun initialize() {
        val saved = this.dogService.saveAll("Willy", "Wulfi", "Rinona", "winona")

        this.dogsRepository.deleteAll()
                .thenMany(saved)
                .thenMany(this.dogsRepository.findAll())
                .subscribe(logger::info)
    }
}
```

> Note that the dog `winona` is spelled with a lower case `w`.

When we run the application, an error occurs and the data is rolled back. Please verify this again in your postgres db.

### Step 4 - Server Send Events

This shall be all for the databases. Next we want to focus on a non blocking way to stream data to a frontend.

The goal of the Step is to provide an endpoint at `http://localhost:8080/dogs/stream` which will stream an endless 
stream of Greeting messages as server send events.

We first create a data class for our Greetings:

```kotlin
data class Greeting(val message: String)
```

Then we create a Service which will generate the endless stream. This time, we will use kotlin co-routines instead of
WebFlux; do demostrate the fluent syntax:

```kotlin
@Service
class GreetingService() {
    @FlowPreview
    fun greet(): Flow<Greeting> = flow {
        while (true) {
            emit(Greeting("Hello SDC @ HOME # ${Instant.now()}"))
            delay(Duration.ofSeconds(1).toMillis())
        }
    }
}
```

> Note the delay method. We cannot block the thread as it would totally defeat the purpose. So instead we use delay,
> which will tell the scheduler to do other task while we wait for the next Greeting to emit.

Finally we create the route mapping:

```kotlin
@Configuration
class RouterConfiguration(val greetingService: GreetingService) {

    @FlowPreview
    @Bean
    fun routerFunction() = coRouter {
        GET("/"){ServerResponse.ok().renderAndAwait("index")}
        "dogs".nest {
            GET("stream", accept(MediaType.TEXT_EVENT_STREAM)){ServerResponse.ok().sse().bodyAndAwait(greetingService.greet())}
        }
    }
}
```

If you hit `http://localhost:8080` now, you should see a new Greeting arriving every second.

But where is the html coming from? On that endppoint, the 
[index.mustache](backend/src/main/resources/templates/index.mustache) template is rendered:

```mustache
<htmls>
<head>
    <script src="require.min.js"></script>
</head>
<body>
    <h1>Reactive Spring Demo Frontend</h1>
    <h3>Here be messages:</h3>
    <div id="messages"></div>
    <script>
        require(['ractive-spring-demo-frontend']);
    </script>
</body>
</htmls>
```

> Note that there is a div with the id `messages` as a container for all received Greetings.

As we see, there is a javascript loaded with require called `ractive-spring-demo-frontend`. This javascript is
actually compiled from kotlin in [Main.kt](frontend/src/main/kotlin/reservations/frontend/Main.kt).

```kotlin
val messageContainer = document.getElementById("messages") as HTMLDivElement

    EventSource("/dogs/stream").asFlow()
            .map { JSON.parse<Message>(it) }
            .collect {
                val div = document.createElement("div").apply {
                    innerHTML = "<p>${it.message}</p>"
                }
                messageContainer.appendChild(div)
            }

```

Here we subscribe to `http://localhost:8080/dogs/stream` and append a new div with the message in the dom under
the message container.

