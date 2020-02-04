package com.example.dogs

import io.r2dbc.spi.ConnectionFactory
import org.apache.logging.log4j.kotlin.Logging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.event.EventListener
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.connectionfactory.R2dbcTransactionManager
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.TransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.util.Assert
import reactor.core.publisher.Flux

@SpringBootApplication
class DogsApplication

fun main(args: Array<String>) {
    runApplication<DogsApplication>(*args)
}

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
        val saved = this.dogService.saveAll("Willy", "Wulfi", "Rinona", "winona")

        this.dogsRepository.deleteAll()
                .thenMany(saved)
                .thenMany(this.dogsRepository.findAll())
                .subscribe(logger::info)
    }
}

interface DogsRepository : ReactiveCrudRepository<Dog, Int>

data class Dog(@Id val id: Int?, val name: String)
