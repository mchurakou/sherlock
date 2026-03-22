package com.example.sherlock.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "message")
class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    var chat: Chat? = null,

    @Column(nullable = false, columnDefinition = "text")
    var content: String = "",

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var role: MessageRole = MessageRole.USER,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "image_data", columnDefinition = "text")
    var imageData: String? = null,

    @Column(name = "image_mime_type", length = 50)
    var imageMimeType: String? = null
)
