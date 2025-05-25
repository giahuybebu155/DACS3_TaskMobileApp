package com.example.taskapplication.domain.model

/**
 * Enum class representing different roles in a team with their permissions
 */
enum class TeamRole(val roleName: String, val permissions: Set<TeamPermission>) {
    ADMIN("admin", setOf(
        TeamPermission.VIEW_TEAM,
        TeamPermission.EDIT_TEAM,
        TeamPermission.DELETE_TEAM,
        TeamPermission.INVITE_MEMBERS,
        TeamPermission.REMOVE_MEMBERS,
        TeamPermission.CHANGE_MEMBER_ROLES,
        TeamPermission.CREATE_TASKS,
        TeamPermission.ASSIGN_TASKS,
        TeamPermission.EDIT_ANY_TASK,
        TeamPermission.DELETE_ANY_TASK,
        TeamPermission.SEND_MESSAGES,
        TeamPermission.DELETE_ANY_MESSAGE
    )),
    
    MODERATOR("moderator", setOf(
        TeamPermission.VIEW_TEAM,
        TeamPermission.INVITE_MEMBERS,
        TeamPermission.CREATE_TASKS,
        TeamPermission.ASSIGN_TASKS,
        TeamPermission.EDIT_ANY_TASK,
        TeamPermission.DELETE_ANY_TASK,
        TeamPermission.SEND_MESSAGES,
        TeamPermission.DELETE_ANY_MESSAGE
    )),
    
    MEMBER("member", setOf(
        TeamPermission.VIEW_TEAM,
        TeamPermission.CREATE_TASKS,
        TeamPermission.EDIT_OWN_TASK,
        TeamPermission.DELETE_OWN_TASK,
        TeamPermission.SEND_MESSAGES,
        TeamPermission.DELETE_OWN_MESSAGE
    )),
    
    GUEST("guest", setOf(
        TeamPermission.VIEW_TEAM,
        TeamPermission.SEND_MESSAGES,
        TeamPermission.DELETE_OWN_MESSAGE
    ));
    
    companion object {
        /**
         * Get TeamRole from string role name
         */
        fun fromString(roleName: String): TeamRole {
            return values().find { it.roleName.equals(roleName, ignoreCase = true) } ?: MEMBER
        }
    }
}

/**
 * Enum class representing different permissions in a team
 */
enum class TeamPermission {
    VIEW_TEAM,               // Can view team details and members
    EDIT_TEAM,               // Can edit team name, description, etc.
    DELETE_TEAM,             // Can delete the team
    INVITE_MEMBERS,          // Can invite new members
    REMOVE_MEMBERS,          // Can remove members
    CHANGE_MEMBER_ROLES,     // Can change roles of members
    CREATE_TASKS,            // Can create new tasks
    ASSIGN_TASKS,            // Can assign tasks to members
    EDIT_OWN_TASK,           // Can edit tasks created by self
    EDIT_ANY_TASK,           // Can edit any task
    DELETE_OWN_TASK,         // Can delete tasks created by self
    DELETE_ANY_TASK,         // Can delete any task
    SEND_MESSAGES,           // Can send messages in team chat
    DELETE_OWN_MESSAGE,      // Can delete own messages
    DELETE_ANY_MESSAGE       // Can delete any message
}
