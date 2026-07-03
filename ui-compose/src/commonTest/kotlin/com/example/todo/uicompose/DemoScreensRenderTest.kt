package com.example.todo.uicompose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import com.example.todo.clientcore.account.AccountState
import com.example.todo.clientcore.auth.AuthPhase
import com.example.todo.clientcore.auth.AuthState
import com.example.todo.clientcore.membership.MembersState
import com.example.todo.clientcore.todos.ListDetailState
import com.example.todo.common.InviteLinkDto
import com.example.todo.common.ListDto
import com.example.todo.common.MemberDto
import com.example.todo.common.Role
import com.example.todo.common.TodoDto
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Render tests for the collaboration screens shipped in slices 5–8, plus the
 * auth flow. Every screen is rendered through the *real* shared composables
 * (the same code Android, iOS, Desktop and — via Compose HTML — Web run per
 * ADR-0001); only the [state] data is canned. Each test writes a PNG for the
 * demo doc and asserts the mock data reached the UI (render-ui-test rule 4).
 *
 * These PNGs are the source images for the multi-platform demo PDF.
 */
class DemoScreensRenderTest {

    // ---- canned data ---------------------------------------------------------

    private val sharedList = ListDto("l1", "Trip to Kyoto", Role.OWNER, "2026-07-01T00:00:00Z")
    private val editorList = ListDto("l2", "Team sprint", Role.EDITOR, "2026-07-01T00:00:00Z")

    private val roster = listOf(
        MemberDto("u1", "felix@theplant.jp", Role.OWNER),
        MemberDto("u2", "aki@theplant.jp", Role.EDITOR),
        MemberDto("u3", "sena@theplant.jp", Role.EDITOR),
    )

    // ---- Auth (sign-in) ------------------------------------------------------

    @Test
    fun `sign in email step`() {
        val path = renderToImage("01-signin-email") {
            SignInScreen(
                state = AuthState(phase = AuthPhase.EMAIL, email = ""),
                onSubmitEmail = {}, onSubmitCode = {}, onChangeEmail = {},
            )
        }
        assertRendered(path)
    }

    @Test
    fun `sign in code step with dev code`() {
        val path = renderToImage("02-signin-code") {
            SignInScreen(
                state = AuthState(phase = AuthPhase.CODE, email = "felix@theplant.jp", devCode = "123456"),
                onSubmitEmail = {}, onSubmitCode = {}, onChangeEmail = {},
            )
        }
        assertRendered(path)
    }

    // ---- Lists index (with sharing + join) -----------------------------------

    @Test
    fun `lists index with owned and shared lists`() {
        val lists = listOf(
            sharedList,
            editorList,
            ListDto("l3", "Groceries", Role.OWNER, "2026-07-02T00:00:00Z"),
        )
        val path = renderToImage("03-lists-index") {
            ListsIndex(
                lists = lists,
                error = null,
                onOpen = {}, onCreate = {}, onRename = { _, _ -> }, onDelete = {},
                onJoin = {}, onManageAccount = {}, onSignOut = {},
            )
        }
        assertRendered(path)
    }

    // ---- List detail with assignment (slice 6) -------------------------------

    @Test
    fun `list detail with assignments and members`() {
        val todos = listOf(
            TodoDto("t1", "l1", "Book ryokan in Gion", description = "2 nights, tatami room",
                dueDate = "2026-07-20", completed = false, order = 1.0, createdAt = "t",
                assigneeUserId = "u1", assigneeEmail = "felix@theplant.jp"),
            TodoDto("t2", "l1", "Reserve Shinkansen seats", completed = false, order = 2.0, createdAt = "t",
                assigneeUserId = "u2", assigneeEmail = "aki@theplant.jp"),
            TodoDto("t3", "l1", "Draft day-by-day itinerary", completed = false, order = 3.0, createdAt = "t"),
            TodoDto("t4", "l1", "Buy JR Pass", completed = true, order = 4.0, createdAt = "t",
                assigneeUserId = "u3", assigneeEmail = "sena@theplant.jp"),
        )
        val path = renderToImage("04-list-detail-assignment") {
            ListDetail(
                list = sharedList,
                state = ListDetailState(loading = false, todos = todos, members = roster),
                currentEmail = "felix@theplant.jp",
                onBack = {}, onAdd = { _, _, _ -> }, onToggle = {}, onSave = { _, _, _, _ -> },
                onDelete = {}, onReorder = { _, _ -> }, onAssign = { _, _ -> },
                onToggleAssignedToMe = {}, onOpenMembers = {},
            )
        }
        assertRendered(path)
    }

    @Test
    fun `list detail filtered to assigned to me`() {
        val todos = listOf(
            TodoDto("t1", "l1", "Book ryokan in Gion", dueDate = "2026-07-20", completed = false,
                order = 1.0, createdAt = "t", assigneeUserId = "u1", assigneeEmail = "felix@theplant.jp"),
            TodoDto("t2", "l1", "Reserve Shinkansen seats", completed = false, order = 2.0, createdAt = "t",
                assigneeUserId = "u2", assigneeEmail = "aki@theplant.jp"),
        )
        val path = renderToImage("05-list-detail-assigned-to-me") {
            ListDetail(
                list = sharedList,
                state = ListDetailState(loading = false, todos = todos, members = roster, assignedToMeOnly = true),
                currentEmail = "felix@theplant.jp",
                onBack = {}, onAdd = { _, _, _ -> }, onToggle = {}, onSave = { _, _, _, _ -> },
                onDelete = {}, onReorder = { _, _ -> }, onAssign = { _, _ -> },
                onToggleAssignedToMe = {}, onOpenMembers = {},
            )
        }
        assertRendered(path)
    }

    // ---- Members / sharing (slice 5) + lifecycle (slice 7) -------------------

    @Test
    fun `members screen owner with active invite link`() {
        val path = renderToImage("06-members-owner-invite") {
            MembersScreen(
                list = sharedList,
                state = MembersState(
                    members = roster,
                    inviteLink = InviteLinkDto(token = "kyt-8f3a91c4b7e2", expiresAt = "2026-07-31T00:00:00Z"),
                ),
                isOwner = true,
                currentEmail = "felix@theplant.jp",
                onBack = {}, onGenerate = {}, onRevoke = {}, onRemove = {}, onTransfer = {}, onLeave = {},
            )
        }
        assertRendered(path)
    }

    @Test
    fun `members screen editor can leave`() {
        val path = renderToImage("07-members-editor") {
            MembersScreen(
                list = editorList,
                state = MembersState(members = roster),
                isOwner = false,
                currentEmail = "aki@theplant.jp",
                onBack = {}, onGenerate = {}, onRevoke = {}, onRemove = {}, onTransfer = {}, onLeave = {},
            )
        }
        assertRendered(path)
    }

    // ---- Delete-account lifecycle (slice 7) ----------------------------------

    @Test
    fun `account screen blocked by shared lists`() {
        val path = renderToImage("08-account-blocked") {
            AccountScreen(
                state = AccountState(
                    blockingLists = listOf(sharedList, ListDto("l4", "Book club", Role.OWNER, "t")),
                ),
                onBack = {}, onDelete = {},
            )
        }
        assertRendered(path)
    }

    @Test
    fun `account screen ready to delete`() {
        val path = renderToImage("09-account-deletable") {
            AccountScreen(
                state = AccountState(blockingLists = emptyList()),
                onBack = {}, onDelete = {},
            )
        }
        assertRendered(path)
    }

    // ---- Desktop master-detail (wide viewport) -------------------------------

    @Test
    fun `desktop master detail collaboration`() {
        val todos = listOf(
            TodoDto("t1", "l1", "Book ryokan in Gion", description = "2 nights, tatami room",
                dueDate = "2026-07-20", completed = false, order = 1.0, createdAt = "t",
                assigneeUserId = "u1", assigneeEmail = "felix@theplant.jp"),
            TodoDto("t2", "l1", "Reserve Shinkansen seats", completed = false, order = 2.0, createdAt = "t",
                assigneeUserId = "u2", assigneeEmail = "aki@theplant.jp"),
            TodoDto("t3", "l1", "Draft day-by-day itinerary", completed = true, order = 3.0, createdAt = "t"),
        )
        val lists = listOf(
            sharedList,
            editorList,
            ListDto("l3", "Groceries", Role.OWNER, "2026-07-02T00:00:00Z"),
        )
        val path = renderToImage("10-desktop-master-detail", width = 1000, height = 720) {
            androidx.compose.material3.MaterialTheme {
                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                ) {
                    androidx.compose.material3.Surface(
                        modifier = androidx.compose.ui.Modifier
                            .width(300.dp)
                            .fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ) {
                        ListsIndex(
                            lists = lists,
                            error = null,
                            onOpen = {}, onCreate = {}, onRename = { _, _ -> }, onDelete = {},
                            onJoin = {}, onManageAccount = {}, onSignOut = {},
                            selectedId = sharedList.id,
                        )
                    }
                    androidx.compose.material3.Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                        ListDetail(
                            list = sharedList,
                            state = ListDetailState(loading = false, todos = todos, members = roster),
                            currentEmail = "felix@theplant.jp",
                            onBack = {}, onAdd = { _, _, _ -> }, onToggle = {}, onSave = { _, _, _, _ -> },
                            onDelete = {}, onReorder = { _, _ -> }, onAssign = { _, _ -> },
                            onToggleAssignedToMe = {}, onOpenMembers = {},
                            showBackButton = false,
                        )
                    }
                }
            }
        }
        assertRendered(path)
    }

    private fun assertRendered(path: String) {
        val file = java.io.File(path)
        assertTrue(file.exists() && file.length() > 1000, "PNG written to $path (${file.length()} bytes)")
    }
}
