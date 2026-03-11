Task Management Mobile Application.

An Android application designed to help individuals and teams manage tasks, collaborate efficiently, and communicate in real time.
This project was developed as part of the DACS3 course project and focuses on building a modern task management system with offline-first capability, team collaboration, and scalable architecture. 
LINK BÁO CÁO: https://drive.google.com/drive/folders/1r6cw-OJrFhWrV8mZAD60MKmnTe88rFi8?usp=sharing
📖 Project Description

The purpose of this project is to develop a mobile task management platform that supports both personal productivity and team collaboration.
The system integrates multiple features such as task organization, Kanban boards, document management, and group chat into a single application.
The application is built using modern Android development technologies and follows Clean Architecture with MVVM to ensure maintainability, scalability, and high performance. 

✨ Key Features
👤 User Management

- User registration and login
- Authentication using email/password

- User profile management

- Role-based access control

📝 Personal Task Management

- Create, update, and delete tasks

- Set deadlines and priorities

- Mark tasks as completed

- Task filtering and organization

👥 Team Collaboration

- Create and manage teams
- Invite and manage members
- Assign tasks to team members
- Manage roles within teams

📊 Kanban Board

- Visual task organization
- Drag-and-drop task movement
- Track task progress through different stages

💬 Real-time Group Chat

- Instant messaging between team members
- Message synchronization
- File attachments

📂 Document Management

- Upload and download documents
- Organize files within projects

🔔 Notifications

- Task deadline reminders
- Notifications for team activities

🌐 Offline-first Support

Application can operate without internet
Local data storage with Room Database
Automatic synchronization when connection is restored

🏗 System Architecture

The application follows Clean Architecture combined with the MVVM design pattern.

Presentation Layer
│
├── UI (Jetpack Compose)
├── ViewModel
│
Domain Layer
│
├── UseCases
├── Business Logic
│
Data Layer
│
├── Repository
├── Local Database (Room)
├── Remote API

This architecture ensures:
Clear separation of responsibilities
Easier maintenance
Scalability for future development
Better testability of application components

⚙️ Technology Stack
Mobile Development
Kotlin
Jetpack Compose
Android SDK
Architecture
MVVM (Model – View – ViewModel)
Clean Architecture
Repository Pattern
Local Data Storage
Room Database
Networking
RESTful API
WebSocket (for real-time chat)
Android Libraries
Retrofit
Hilt (Dependency Injection)
Coroutines & Flow
WorkManager


