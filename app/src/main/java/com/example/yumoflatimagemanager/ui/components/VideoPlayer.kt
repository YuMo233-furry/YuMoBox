package com.example.yumoflatimagemanager.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView

@Composable
fun VideoPlayer(uri: Uri, playWhenReady: Boolean = true) {
	val context = LocalContext.current
	val player = remember {
		SimpleExoPlayer.Builder(context).build().apply {
			setMediaItem(MediaItem.Builder().setUri(uri).build())
			prepare()
			this.playWhenReady = playWhenReady
		}
	}
	DisposableEffect(Unit) {
		onDispose { player.release() }
	}
	AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player } })
}


