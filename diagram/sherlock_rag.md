### Sherlock Architecture — with RAG

```mermaid
graph TB
    UI(["Angular UI"])
    BE(["Spring Boot"])
    DB[("PostgreSQL")]
    LLM(["LLM API"])
    QD[("Qdrant")]:::new

    UI -->|"1. REST"| BE
    DB <-->|"2. retrieve history"| BE
    LLM <-->|"3. query embedding"| BE
    BE <--> |"4. vector search"| QD
    BE <-->|"5. stream SSE"| LLM
    BE -->|"6. token chunks SSE"| UI
    BE -->|"7. persist messages"| DB

    classDef new fill:#2d6a2d,stroke:#5cb85c,color:#fff
    linkStyle 2 stroke:#5cb85c,stroke-width:2px
    linkStyle 3 stroke:#5cb85c,stroke-width:2px
```
