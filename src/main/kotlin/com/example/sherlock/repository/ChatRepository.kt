package com.example.sherlock.repository

import com.example.sherlock.entity.Chat
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface ChatRepository : JpaRepository<Chat, Long> {

    @Query("SELECT c FROM Chat c LEFT JOIN FETCH c.messages WHERE c.id = :id")
    fun findByIdWithMessages(id: Long): Optional<Chat>
}
