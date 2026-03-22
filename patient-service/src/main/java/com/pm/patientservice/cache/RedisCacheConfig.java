package com.pm.patientservice.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory){

        ObjectMapper objectMapper = new ObjectMapper(); // we use object mapper class to handle how conversion (serializing/deserializing) happens , without this redis might use its default serializing/deserializing methods which can cause all kinds of problems
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS); // disables redis from storing date as unix time and forces it to store it in human-readable format
        objectMapper.activateDefaultTyping( // it is for redis to help it while reconstructing certain objects during cache retrieval , when we write this jackson stores the type information when serializing the object
                LaissezFaireSubTypeValidator.instance, // which then redis can use to deserialize correctly when it retrieves the object from cache
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper); // converts java objects to and from json using the object mapper we created

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig() // create the config we want
                .entryTtl(Duration.ofMinutes(10)) // store for 10 mins after that evict the entry
                .disableCachingNullValues()  // don't store null
                .serializeKeysWith(  // tells redis how to serialize the key , we just use the default redis string serializer
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith( //tells redis how to serialize the value , we use the serializer (indirectly object mapper) that we created above
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        return RedisCacheManager.builder(connectionFactory) //create the cache manager bean using the above created config and connects to Redis using connection factory
                .cacheDefaults(config).build();

    }

}
