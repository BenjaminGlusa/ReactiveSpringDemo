package com.example.dogs

import org.apache.logging.log4j.kotlin.Logging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@SpringBootApplication
class DogsApplication

fun main(args: Array<String>) {
    runApplication<DogsApplication>(*args)
}

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

interface DogsRepository : ReactiveCrudRepository<Dog, String>

@Document
data class Dog(@Id val id: String?, val name: String)
