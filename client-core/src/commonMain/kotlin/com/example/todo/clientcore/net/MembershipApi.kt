package com.example.todo.clientcore.net

import com.example.todo.common.ApiRoutes
import com.example.todo.common.CreateInviteLinkRequest
import com.example.todo.common.InviteLinkDto
import com.example.todo.common.InvitePreviewDto
import com.example.todo.common.ListDto
import com.example.todo.common.MemberDto
import io.ktor.client.call.body

/** Sharing & membership against the server (slice 5), authenticated via [AuthorizedApi]. */
class MembershipApi(private val api: AuthorizedApi) {

    suspend fun members(listId: String): List<MemberDto> =
        api.get(ApiRoutes.members(listId)).body()

    /** The active Invite Link, or null when there is none (server returns 404). */
    suspend fun getInviteLink(listId: String): InviteLinkDto? =
        try {
            api.get(ApiRoutes.inviteLink(listId)).body()
        } catch (e: ApiException) {
            if (e.status == 404) null else throw e
        }

    suspend fun generateInviteLink(listId: String, expiresAt: String? = null): InviteLinkDto =
        api.post(ApiRoutes.inviteLink(listId), CreateInviteLinkRequest(expiresAt)).body()

    suspend fun revokeInviteLink(listId: String) {
        api.delete(ApiRoutes.inviteLink(listId))
    }

    /** Remove a member, or leave the List (when [userId] is the caller's own id). */
    suspend fun removeMember(listId: String, userId: String) {
        api.delete(ApiRoutes.member(listId, userId))
    }

    suspend fun preview(token: String): InvitePreviewDto =
        api.get(ApiRoutes.invitePreview(token)).body()

    suspend fun join(token: String): ListDto =
        api.post(ApiRoutes.inviteJoin(token)).body()
}
