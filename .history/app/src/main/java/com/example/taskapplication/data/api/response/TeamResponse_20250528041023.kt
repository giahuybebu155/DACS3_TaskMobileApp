package com.example.taskapplication.data.api.response

import com.google.gson.annotations.SerializedName

/**
 * Team response từ API GET /teams - phù hợp với Laravel API structure
 */
data class TeamResponse(
    @SerializedName("id")
    val id: String,  // Sử dụng UUID từ API

    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("created_by")
    val created_by: Long,

    @SerializedName("uuid")
    val uuid: String,

    @SerializedName("created_at")
    val created_at: String,

    @SerializedName("updated_at")
    val updated_at: String,

    @SerializedName("members")
    val members: List<TeamMemberApiResponse>? = null,

    @SerializedName("creator")
    val creator: UserResponse? = null
)

/**
 * Team member response từ API - nested trong TeamResponse
 */
data class TeamMemberApiResponse(
    @SerializedName("id")
    val id: Long,

    @SerializedName("team_id")
    val team_id: Long,

    @SerializedName("user_id")
    val user_id: Long,

    @SerializedName("role")
    val role: String,

    @SerializedName("joined_at")
    val joined_at: String,

    @SerializedName("created_at")
    val created_at: String,

    @SerializedName("updated_at")
    val updated_at: String,

    @SerializedName("invited_by")
    val invited_by: Long? = null,

    @SerializedName("user")
    val user: UserResponse? = null
)
