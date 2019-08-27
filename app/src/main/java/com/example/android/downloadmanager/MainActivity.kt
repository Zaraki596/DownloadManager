package com.example.android.downloadmanager

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2.util.toDownloadInfo
import com.tonyodev.fetch2core.DownloadBlock
import com.tonyodev.fetch2core.Func
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.download_item.*
import java.io.File


class MainActivity : AppCompatActivity() {

    //Setting download Id for the per download
    private lateinit var fetch: Fetch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val fetchConfiguration: FetchConfiguration = FetchConfiguration.Builder(this)
            .enableLogging(true)
            .enableRetryOnNetworkGain(true)
            .setDownloadConcurrentLimit(4).build()
        fetch = Fetch.Impl.getInstance(fetchConfiguration)




        initateWebView()

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1
            )
        }

    }


    @SuppressLint("SetJavaScriptEnabled")
    private fun initateWebView() {
        webview_dwnld.loadUrl("https://www.google.com")
        webview_dwnld.settings.javaScriptEnabled = true


        /*
        Setting Up the webView Client*/
        webview_dwnld.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                view?.loadUrl(url)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                group.visibility = View.VISIBLE
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {

                /*To maintain the session of the user We use flush() method this maintain the session unless logged out
                * See official documentation fo rmore details
                 * */
                val cookieManager: CookieManager = CookieManager.getInstance()
                cookieManager.flush()
                group.visibility = View.GONE
                super.onPageFinished(view, url)

            }
        }
        /*
        Setting Up the web chrome client*/
        webview_dwnld.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                pb_loading.progress = newProgress
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                iv_favicon.setImageBitmap(icon)

            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                supportActionBar?.title = title
            }
        }

        /*
        To download the file we have to implement this method , Used third party library*/
        downLoadFile()
    }

    private fun downLoadFile() {
        webview_dwnld.setDownloadListener { url, userAgent, contentDescription, mimetype, contentLength ->
            /*Downloaded File Path*/
            val dirPath: String = getExternalFilesDir(null)!!.absolutePath
            /*Parsing the name from the url*/
            val fileName: String = URLUtil.guessFileName(url, contentDescription, mimetype)


            val request: Request = Request(url, dirPath)
            request.priority = Priority.HIGH
            request.networkType = NetworkType.ALL

            fetch.enqueue(request, object : Func<Request> {
                override fun call(result: Request) {
                    Toast.makeText(this@MainActivity, "Call at the enquee", Toast.LENGTH_SHORT).show()
                }
            }, object : Func<Error> {
                override fun call(result: Error) {
                    Toast.makeText(this@MainActivity, "Error at the enquee", Toast.LENGTH_SHORT).show()
                }
            })


            val fetchListener: FetchListener = object : FetchListener {
                override fun onAdded(download: Download) {
                    if (request.id == download.id) {
                        tv_file_size_total.text = (download.total/8000).toString()
                        tv_dwnldng_file_name.text = fileName
                    }
                }

                override fun onCancelled(download: Download) {
                    Toast.makeText(this@MainActivity, "Download Cancelled", Toast.LENGTH_SHORT).show()
                }

                override fun onCompleted(download: Download) {
                    Toast.makeText(this@MainActivity, "File Downloaded", Toast.LENGTH_SHORT).show()
                }

                override fun onDeleted(download: Download) {
                    Toast.makeText(this@MainActivity, "File Deleted", Toast.LENGTH_SHORT).show()
                }

                override fun onDownloadBlockUpdated(
                    download: Download,
                    downloadBlock: DownloadBlock,
                    totalBlocks: Int
                ) {
                    Toast.makeText(this@MainActivity, "Download BLock Updated", Toast.LENGTH_SHORT).show()
                }

                override fun onError(download: Download, error: Error, throwable: Throwable?) {
                    Toast.makeText(this@MainActivity, "Error occurred : $error", Toast.LENGTH_SHORT)
                        .show()
                }

                override fun onPaused(download: Download) {
                    iv_pause_resume.setImageResource(R.drawable.ic_play)
                    iv_pause_resume.setOnClickListener {
                        fetch.resume(request.id)
                    }
                }

                override fun onProgress(
                    download: Download,
                    etaInMilliSeconds: Long,
                    downloadedBytesPerSecond: Long
                ) {
                        if(request.id == download.id) {


                            pb_dwnlding.progress = download.progress
                            tv_file_size_total.text =
                                download.downloaded.toString() + "/" + download.total.toString()
                            tv_spd_paus.text = (downloadedBytesPerSecond / 8000).toString()
                        }

                }

                override fun onQueued(download: Download, waitingOnNetwork: Boolean) {
                    if(request.id == download.id) {
                        Toast.makeText(this@MainActivity, "On queued", Toast.LENGTH_SHORT).show()
                    }

                }

                override fun onRemoved(download: Download) {
                    Toast.makeText(this@MainActivity, "ON Removed Called", Toast.LENGTH_SHORT).show()
                }

                override fun onResumed(download: Download) {
                    if(request.id == download.id) {
                        Toast.makeText(this@MainActivity, "On resumed Called", Toast.LENGTH_SHORT)
                            .show()
                        iv_pause_resume.setOnClickListener {
                            fetch.pause(request.id)
                        }
                    }
                }


                override fun onStarted(
                    download: Download,
                    downloadBlocks: List<DownloadBlock>,
                    totalBlocks: Int
                ) {
                    if (request.id == download.id){

                        Toast.makeText(this@MainActivity, "On started Called", Toast.LENGTH_SHORT).show()
                    }

                }

                override fun onWaitingNetwork(download: Download) {
                }
            }
            fetch.addListener(fetchListener)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fetch.close()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val menuInflater: MenuInflater = menuInflater
        menuInflater.inflate(R.menu.reload_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh -> {
                webview_dwnld.reload()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (webview_dwnld.canGoBack()) {
            webview_dwnld.goBack()
        } else {
            finish()
        }
    }
}

