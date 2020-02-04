package com.example.dogs

import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.apache.logging.log4j.kotlin.Logging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.connectionfactory.R2dbcTransactionManager
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.TransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.util.Assert
import org.springframework.web.reactive.function.server.*
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.Instant

@SpringBootApplication
class DogsApplication

fun main(args: Array<String>) {
    runApplication<DogsApplication>(*args)
}

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

@Service
class GreetingService() {
    @FlowPreview
    fun greet(): Flow<Greeting> = flow {
        while (true) {
            emit(Greeting("Hello OOP @ ${Instant.now()}"))
            delay(Duration.ofSeconds(1).toMillis())
        }
    }
}

data class Greeting(val message: String)

@Bean
fun reactiveTransactionManager(cf: ConnectionFactory) = R2dbcTransactionManager(cf)

@Bean
fun transactionalOperator(reactiveTransactionManager: ReactiveTransactionManager) = TransactionalOperator.create(reactiveTransactionManager)

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

@Component
class InitializeData(
        val dogsRepository: DogsRepository,
        val dogService: DogService
) : Logging {

    @EventListener(ApplicationReadyEvent::class)
    fun initialize() {
        val saved = this.dogService.saveAll("Willy", "Wulfi", "Rinona", "Winona")

        this.dogsRepository.deleteAll()
                .thenMany(saved)
                .thenMany(this.dogsRepository.findAll())
                .subscribe(logger::info)
    }
}

interface DogsRepository : ReactiveCrudRepository<Dog, Int>

data class Dog(@Id val id: Int?, val name: String)
