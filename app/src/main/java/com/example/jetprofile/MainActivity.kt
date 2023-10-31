package com.example.jetprofile

import android.R.attr.value
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.os.HandlerCompat
import com.example.jetprofile.ui.theme.JetProfileTheme
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val directoryPath = Environment.getExternalStorageDirectory().path + "/hatori_picture"

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayDialog()
        setContent {
            JetProfileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    DisplayScreen()
                }
            }
        }
    }

    private fun displayDialog() {
        // 既存ディレクトリの有無を調べる
        if (File(directoryPath).isDirectory) return
        // ダイアログ出力
        val builder = AlertDialog.Builder(this).apply {
            setMessage("ストレージへのアクセス許可")
            setPositiveButton("許可する") { _, _ ->
                createDirectory()
            }
            setNegativeButton("許可しない") { _, _ ->
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        builder.create().apply {
            // キャンセル操作を無効
            setCancelable(false)
        }.show()
    }

    private fun createDirectory() {
        try {
            // 既存ディレクトリの有無を調べる
            val myDirectory = File(directoryPath)
            if (!myDirectory.isDirectory) myDirectory.mkdir()
        } catch (e: SecurityException) {
            // ファイルに書き込み用のパーミッションが無い場合など
            e.printStackTrace()
        } catch (e: IOException) {
            // 何らかの原因で誤ってディレクトリを2回作成してしまった場合など
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("IntentReset", "CoroutineCreationDuringComposition", "SimpleDateFormat")
    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(
        ExperimentalMaterial3Api::class,
        ExperimentalComposeUiApi::class,
        DelicateCoroutinesApi::class
    )
    @Composable
    fun DisplayScreen() {
        ConstraintLayout(modifier = Modifier.fillMaxWidth()) {
            var canDownload by remember { mutableStateOf(false) }
            var inputValue by remember { mutableStateOf("") }
            var isHiddenProgressbar by remember { mutableStateOf(true) }
            var imageBitmap by remember {
                mutableStateOf(
                    BitmapFactory.decodeResource(
                        resources, R.drawable.transparent_image
                    )
                )
            }
            val keyboardController = LocalSoftwareKeyboardController.current
            val (toGalleryButton,
                text,
                editText,
                startDownloadButton,
                progressBar,
                displayImage,
                clearButton,
                downloadedImageButton) = createRefs()
            // ダウンロード処理
            if (canDownload) {
                GlobalScope.launch {
                    try {
                        val url = URL(inputValue)
                        val urlConnection = withContext(Dispatchers.IO) {
                            url.openConnection()
                        } as HttpURLConnection
                        urlConnection.readTimeout = 10000
                        urlConnection.connectTimeout = 20000
                        urlConnection.requestMethod = "GET"
                        // リダイレクトを自動で許可しない設定
                        urlConnection.instanceFollowRedirects = false
                        val bitmap = BitmapFactory.decodeStream(urlConnection.inputStream)
                        // 別スレッド内での処理を管理し実行する
                        HandlerCompat.createAsync(mainLooper).post {
                            Toast.makeText(applicationContext, "画像をダウンロードしました", Toast.LENGTH_LONG)
                                .show()
                            isHiddenProgressbar = true
                            canDownload = false
                            imageBitmap = bitmap
                        }
                        // データ保存のフォーマット
                        val dateFormat = SimpleDateFormat("yyyyMMdd_HH:mm:ss")
                        val currentDate: String = dateFormat.format(Date())
                        // JPEG形式で保存
                        val file = File(directoryPath, "$currentDate.jpeg")
                        withContext(Dispatchers.IO) {
                            FileOutputStream(file).use { stream ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                            }
                        }
                    } catch (e: IOException) {
                        HandlerCompat.createAsync(mainLooper).post {
                            Toast.makeText(
                                applicationContext, "画像をダウンロード出来ませんでした", Toast.LENGTH_LONG
                            ).show()
                            isHiddenProgressbar = true
                            canDownload = false
                        }
                        e.printStackTrace()
                    }
                }
            }
            // 画像をプレビュー
            val launcher =
                rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    imageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }
            // UI表示
            Button(
                onClick = { launcher.launch("image/*") },
                enabled = true,
                border = BorderStroke(1.dp, Color.Black),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.Black,
                ),
                contentPadding = PaddingValues(10.dp),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .padding(10.dp)
                    .constrainAs(toGalleryButton) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                    }) {
                Text(text = "Galleryから選択")
            }
            Text(
                modifier = Modifier
                    .padding(10.dp)
                    .constrainAs(text) {
                        top.linkTo(toGalleryButton.bottom)
                        start.linkTo(parent.start)
                    },
                text = "URLを入力して下さい",
            )
            TextField(
                modifier = Modifier
                    .width(215.dp)
                    .padding(10.dp)
                    .constrainAs(editText) {
                        top.linkTo(text.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(startDownloadButton.start)
                    },
                value = inputValue,
                onValueChange = { inputValue = it },
                singleLine = false,
                label = {
                    Text("http://")
                },
                maxLines = 3
            )
            Button(
                onClick = {
                    keyboardController?.hide()
                    isHiddenProgressbar = false
                    canDownload = true
                },
                enabled = true,
                border = BorderStroke(1.dp, Color.Black),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.Black,
                ),
                contentPadding = PaddingValues(10.dp),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .padding(10.dp)
                    .constrainAs(startDownloadButton) {
                        top.linkTo(editText.top)
                        bottom.linkTo(editText.bottom)
                        end.linkTo(parent.end)
                    }) {
                Text(text = "ダウンロード開始")
            }
            Image(
                bitmap = imageBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(1.0f)
                    .fillMaxHeight(0.5f)
                    .padding(10.dp)
                    .constrainAs(displayImage) {
                        top.linkTo(editText.bottom)
                        bottom.linkTo(clearButton.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    })
            if (!isHiddenProgressbar) {
                CircularProgressIndicator(modifier = Modifier.constrainAs(progressBar) {
                    top.linkTo(displayImage.top)
                    bottom.linkTo(displayImage.bottom)
                    start.linkTo(displayImage.start)
                    end.linkTo(displayImage.end)
                })
            }
            Button(
                onClick = {
                    inputValue = ""
                    imageBitmap = BitmapFactory.decodeResource(
                        resources, R.drawable.transparent_image
                    )
                },
                enabled = true,
                border = BorderStroke(1.dp, Color.Black),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.Black,
                ),
                contentPadding = PaddingValues(10.dp),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .padding(10.dp)
                    .constrainAs(clearButton) {
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(downloadedImageButton.start)
                    }) {
                Text(text = "Clear")
            }
            Button(
                onClick = { launcher.launch("image/*") },
                enabled = true,
                border = BorderStroke(1.dp, Color.Black),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.Black,
                ),
                contentPadding = PaddingValues(10.dp),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .padding(10.dp)
                    .constrainAs(downloadedImageButton) {
                        bottom.linkTo(parent.bottom)
                        start.linkTo(clearButton.end)
                        end.linkTo(parent.end)
                    }) {
                Text(text = "ダウンロードした画像")
            }
        }
    }
}

