package com.example.todo.clientcore.membership

import com.example.todo.clientcore.net.MembershipApi
import com.example.todo.common.InviteLinkDto
import com.example.todo.common.MemberDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI-facing state for a List's members + Invite Link (slice 5, seam 2). */
data class MembersState(
    val loading: Boolean = false,
    val members: List<MemberDto> = emptyList(),
    /** The active Invite Link, or null. Only fetched/managed by the Owner. */
    val inviteLink: InviteLinkDto? = null,
    val error: String? = null,
)

/**
 * Drives the members + sharing panel shared by all four clients (ADR-0001).
 * Owner-only actions (generate/revoke link, remove member) are still enforced
 * server-side; [isOwner] only decides which affordances the UI shows and whether
 * to fetch the Invite Link. Every mutation refetches so the client never diverges.
 */
class MembersViewModel(
    private val listId: String,
    private val isOwner: Boolean,
    private val api: MembershipApi,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(MembersState())
    val state: StateFlow<MembersState> = _state.asStateFlow()

    fun load(): Job = scope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        _state.value = try {
            MembersState(
                loading = false,
                members = api.members(listId),
                inviteLink = if (isOwner) api.getInviteLink(listId) else null,
            )
        } catch (e: Exception) {
            _state.value.copy(loading = false, error = e.message ?: "Could not load members")
        }
    }

    fun generateInviteLink(expiresAt: String? = null): Job =
        mutateThenReload { api.generateInviteLink(listId, expiresAt) }

    fun revokeInviteLink(): Job = mutateThenReload { api.revokeInviteLink(listId) }

    /** Owner removes another member. */
    fun removeMember(userId: String): Job = mutateThenReload { api.removeMember(listId, userId) }

    private fun mutateThenReload(op: suspend () -> Unit): Job = scope.launch {
        _state.value = _state.value.copy(error = null)
        try {
            op()
            _state.value = _state.value.copy(
                members = api.members(listId),
                inviteLink = if (isOwner) api.getInviteLink(listId) else null,
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message ?: "Something went wrong")
        }
    }
}
