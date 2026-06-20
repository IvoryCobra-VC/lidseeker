package com.lidseeker.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Wraps scrollable [content] with Material pull-to-refresh. When the user pulls
 * past the threshold, [onRefresh] runs and the spinner stays until it finishes
 * (onRefresh is awaited). The content must itself be scrollable (e.g. a
 * LazyColumn) for the gesture to register.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefresh(
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    if (state.isRefreshing) {
        LaunchedEffect(Unit) {
            onRefresh()
            state.endRefresh()
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .nestedScroll(state.nestedScrollConnection),
    ) {
        content()
        PullToRefreshContainer(
            state = state,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
