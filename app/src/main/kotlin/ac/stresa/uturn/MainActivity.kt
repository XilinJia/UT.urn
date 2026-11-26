package ac.stresa.uturn

import ac.stresa.uturn.ui.theme.PodciniProviderTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PodciniProviderTheme {
                Box(modifier = Modifier.background(Color.Black).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.main_screen_message), color = Color.Green)
                }
            }
        }
    }
}
