package com.gastos.compartidos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.gastos.compartidos.ui.auth.AuthScreen
import com.gastos.compartidos.ui.expense.AddExpenseScreen
import com.gastos.compartidos.ui.groupdetail.GroupDetailScreen
import com.gastos.compartidos.ui.groups.GroupListScreen
import com.gastos.compartidos.ui.join.JoinGroupScreen
import com.google.firebase.auth.FirebaseAuth

object Routes {
    const val AUTH = "auth"
    const val GROUPS = "groups"
    const val GROUP_DETAIL = "group/{groupId}"
    const val ADD_EXPENSE = "group/{groupId}/add"
    const val EDIT_EXPENSE = "group/{groupId}/edit/{entryId}"
    const val JOIN = "join/{code}"

    fun groupDetail(groupId: String) = "group/$groupId"
    fun addExpense(groupId: String) = "group/$groupId/add"
    fun editExpense(groupId: String, entryId: String) = "group/$groupId/edit/$entryId"
    fun join(code: String) = "join/$code"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val auth = remember { FirebaseAuth.getInstance() }
    var loggedIn by remember { mutableStateOf(auth.currentUser != null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { loggedIn = it.currentUser != null }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    NavHost(
        navController = navController,
        startDestination = if (loggedIn) Routes.GROUPS else Routes.AUTH,
    ) {
        composable(Routes.AUTH) {
            AuthScreen(
                onLoggedIn = {
                    navController.navigate(Routes.GROUPS) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.GROUPS) {
            GroupListScreen(
                onOpenGroup = { navController.navigate(Routes.groupDetail(it)) },
                onJoinWithCode = { navController.navigate(Routes.join(it)) },
                onSignedOut = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.GROUPS) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Routes.JOIN,
            arguments = listOf(navArgument("code") { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://lignario.github.io/join?code={code}" },
            ),
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString("code").orEmpty()
            JoinGroupScreen(
                inviteCode = code,
                onBack = { navController.popBackStack() },
                onJoined = { groupId ->
                    navController.navigate(Routes.groupDetail(groupId)) {
                        popUpTo(Routes.GROUPS)
                    }
                },
            )
        }
        composable(
            Routes.GROUP_DETAIL,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            GroupDetailScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() },
                onAddExpense = { navController.navigate(Routes.addExpense(groupId)) },
                onEditExpense = { entryId ->
                    navController.navigate(Routes.editExpense(groupId, entryId))
                },
            )
        }
        composable(
            Routes.ADD_EXPENSE,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            AddExpenseScreen(
                groupId = groupId,
                onDone = { navController.popBackStack() },
            )
        }
        composable(
            Routes.EDIT_EXPENSE,
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType },
                navArgument("entryId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            val entryId = backStackEntry.arguments?.getString("entryId").orEmpty()
            AddExpenseScreen(
                groupId = groupId,
                entryId = entryId,
                onDone = { navController.popBackStack() },
            )
        }
    }
}
