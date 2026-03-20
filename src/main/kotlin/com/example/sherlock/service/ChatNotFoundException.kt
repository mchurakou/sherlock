package com.example.sherlock.service

class ChatNotFoundException(id: Long) : RuntimeException("Chat not found: $id")
