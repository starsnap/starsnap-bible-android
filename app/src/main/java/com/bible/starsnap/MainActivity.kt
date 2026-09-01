package com.bible.starsnap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.bible.starsnap.ui.BibleViewModel
import com.bible.starsnap.ui.SessionViewModel
import com.bible.starsnap.ui.StarSnapBibleApp
import com.bible.starsnap.ui.StarSnapBibleTheme

class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer(applicationContext) }
    private val sessionViewModel by lazy {
        ViewModelProvider(
            this,
            SimpleViewModelFactory {
                SessionViewModel(container.api, container.cookieJar)
            },
        )[SessionViewModel::class.java]
    }
    private val bibleViewModel by lazy {
        ViewModelProvider(
            this,
            SimpleViewModelFactory { BibleViewModel(container.api) },
        )[BibleViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StarSnapBibleTheme {
                StarSnapBibleApp(
                    sessionViewModel = sessionViewModel,
                    bibleViewModel = bibleViewModel,
                )
            }
        }
    }
}

private class SimpleViewModelFactory<T : ViewModel>(
    private val create: () -> T,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>, extras: CreationExtras): VM =
        create() as VM
}
