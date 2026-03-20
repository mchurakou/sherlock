package com.example.sherlock.repository

import com.example.sherlock.entity.Chat
import org.springframework.data.jpa.repository.JpaRepository

interface ChatRepository : JpaRepository<Chat, Long>
