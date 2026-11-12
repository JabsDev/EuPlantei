package br.com.jabsdev.euplantei.ui.screens.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.jabsdev.euplantei.ui.theme.Cream
import br.com.jabsdev.euplantei.ui.theme.ForestGreen
import br.com.jabsdev.euplantei.ui.theme.ForestGreenDark
import br.com.jabsdev.euplantei.ui.theme.InkMuted
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.ui.components.BrandMark
import br.com.jabsdev.euplantei.ui.components.LeafArt
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    onConfigureModel: () -> Unit,
    onDownloadModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = listOf(
        OnboardingPage(Icons.Rounded.Spa, R.string.onboarding_welcome_title, R.string.onboarding_welcome_body),
        OnboardingPage(Icons.Rounded.Lock, R.string.onboarding_privacy_title, R.string.onboarding_privacy_body),
        OnboardingPage(Icons.Rounded.Psychology, R.string.onboarding_model_title, R.string.onboarding_model_body),
    )
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex
    val view = LocalView.current
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onDownloadModel() }

    fun startModelDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onDownloadModel()
        }
    }

    // O topo do onboarding é verde-escuro; os ícones da status bar precisam ser claros.
    DisposableEffect(Unit) {
        val activity = view.context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (previousStatus != null) controller.isAppearanceLightStatusBars = previousStatus
            if (previousNav != null) controller.isAppearanceLightNavigationBars = previousNav
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        ForestGreenDark,
                        ForestGreen,
                        ForestGreenDark,
                    ),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandMark(size = 42)
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = Cream,
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { index ->
            val page = pages[index]
            val scrollable = Modifier.then(
                if (index == 2) Modifier.verticalScroll(rememberScrollState()) else Modifier,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .then(scrollable)
                    .padding(bottom = 16.dp),
                verticalArrangement = if (index == 2) Arrangement.Top else Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (index == 2) Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .size(if (index == 0) 210.dp else if (index == 2) 112.dp else 148.dp)
                        .clip(CircleShape)
                        .background(Cream),
                    contentAlignment = Alignment.Center,
                ) {
                    if (index == 0) {
                        LeafArt(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(26.dp),
                            leafColor = ForestGreen,
                            stemColor = ForestGreenDark,
                        )
                    } else {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            tint = ForestGreen,
                            modifier = Modifier.size(64.dp),
                        )
                    }
                }
                Spacer(Modifier.height(if (index == 2) 18.dp else 30.dp))
                Text(
                    text = stringResource(page.titleRes),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Cream,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(if (index == 2) 10.dp else 14.dp))
                Text(
                    text = stringResource(page.bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Cream.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )

                if (index == 2) {
                    Spacer(Modifier.height(16.dp))
                    ModelOptionCard(
                        icon = Icons.Rounded.Download,
                        title = stringResource(R.string.onboarding_download_model),
                        body = stringResource(R.string.onboarding_download_model_body),
                        onClick = ::startModelDownload,
                    )
                    Spacer(Modifier.height(8.dp))
                    ModelOptionCard(
                        icon = Icons.Rounded.FolderOpen,
                        title = stringResource(R.string.onboarding_import_model),
                        body = stringResource(R.string.onboarding_import_model_body),
                        onClick = onConfigureModel,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 20.dp),
            ) {
                repeat(pages.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(width = if (selected) 28.dp else 9.dp, height = 9.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    Cream
                                } else {
                                    Cream.copy(alpha = 0.35f)
                                },
                            ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    Surface(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        shape = CircleShape,
                        color = Cream,
                        contentColor = ForestGreenDark,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (isLast) {
                            onFinish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier.height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cream,
                        contentColor = ForestGreenDark,
                    ),
                ) {
                    Text(if (isLast) stringResource(R.string.onboarding_start) else stringResource(R.string.common_next))
                    Icon(
                        imageVector = Icons.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp),
                    )
                }
            }
            if (isLast) {
                TextButton(
                    onClick = onFinish,
                    colors = ButtonDefaults.textButtonColors(contentColor = Cream),
                ) {
                    Text(stringResource(R.string.onboarding_configure_later))
                }
                Text(
                    text = stringResource(R.string.onboarding_configure_later_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Cream.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_permission_note),
                style = MaterialTheme.typography.labelSmall,
                color = Cream.copy(alpha = 0.68f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ModelOptionCard(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Cream,
        contentColor = ForestGreenDark,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            ForestGreen.copy(alpha = 0.24f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = ForestGreen,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            Icon(
                Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = ForestGreen,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}


private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
