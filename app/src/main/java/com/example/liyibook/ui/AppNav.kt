package com.example.liyibook.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.liyibook.R
import com.example.liyibook.ui.bookshelf.BookshelfScreen
import com.example.liyibook.ui.detail.DetailScreen
import com.example.liyibook.ui.mine.MineScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppNav(vm: BookViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.toast.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val nav = rememberNavController()
    val tabs = listOf(
        Tab("bookshelf", "书架", ImageVector.vectorResource(R.drawable.ic_shelf)),
        Tab("mine", "我的", ImageVector.vectorResource(R.drawable.ic_mine))
    )

    Scaffold(
        bottomBar = {
            val backStack by nav.currentBackStackEntryAsState()
            val current = backStack?.destination
            if (tabs.any { t -> current?.hierarchy?.any { it.route == t.route } == true }) {
                NavigationBar(
                    containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f)
                ) {
                    tabs.forEach { tab ->
                        val selected = current?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = com.example.liyibook.ui.theme.BrandBlue,
                                selectedTextColor = com.example.liyibook.ui.theme.BrandBlue,
                                indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "bookshelf", Modifier.padding(padding)) {
            composable("bookshelf") { BookshelfScreen(vm = vm, nav = nav) }
            composable("mine") { MineScreen(vm = vm, nav = nav) }
            composable("detail/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                DetailScreen(vm = vm, bookId = id, onBack = { nav.popBackStack() })
            }
        }
    }
}
