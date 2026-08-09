# landonkea-thinkLessScheduleMore — Design & Workflow

## High-Level Overview

```mermaid
graph TB
    subgraph "Shared Design"
        A[Core Loop] --> B[Pick random time]
        B --> C[Pick random message]
        C --> D[Send message]
        D --> E[Log send]
        E --> B
    end

    subgraph "Android (Kotlin)"
        F[SmsManager] --> G[Foreground Service]
        G --> H[SharedPreferences]
    end

    subgraph "iOS (Swift)"
        I[Local Notification] --> J[User taps]
        J --> K[Messages app]
        K --> L[UserDefaults]
    end

    A --> F
    A --> I
```

## Android Flow

```mermaid
sequenceDiagram
    participant A as Android App
    participant S as SmsManager
    participant P as Provider

    A->>A: Schedule random time
    A->>A: Pick random message
    A->>S: Send SMS directly
    S-->>P: Message delivered
    A->>A: Log send
    A->>A: Schedule next time
```

## iOS Flow

```mermaid
sequenceDiagram
    participant I as iOS App
    participant N as Notification
    participant U as User
    participant M as Messages

    I->>I: Schedule random time
    I->>I: Pick random message
    I->>N: Fire local notification
    N->>U: "Thinking of you" alert
    U->>N: Tap notification
    N->>M: Open with pre-filled text
    U->>M: Tap Send
    M-->>I: (manual send)
    I->>I: Log send
    I->>I: Schedule next time
```

## File Relationships

| File | Purpose | Platform |
|------|---------|----------|
| `android/` | Kotlin app | Android |
| `ios/` | Swift app | iOS |
| `shared/ARCHITECTURE.md` | Design notes | Both |
| `scripts/run_all_tests.sh` | Test runner | CI |

## draw.io

[Open in draw.io](https://app.diagrams.net/#RMessage%20scheduling%20architecture)
