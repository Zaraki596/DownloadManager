package com.example.android.downloadmanager

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
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
import com.downloader.*
import com.facebook.network.connectionclass.ConnectionClassManager
import com.facebook.network.connectionclass.DeviceBandwidthSampler
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.download_item.*


class MainActivity : AppCompatActivity() {

    //Setting download Id for the per download
    private var downloadID: Int = 0


    //Initializing facebook connection class library
    val mConnectionClassManager: ConnectionClassManager = ConnectionClassManager.getInstance()
    val mDeviceBandwidthSampler: DeviceBandwidthSampler = DeviceBandwidthSampler.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        //Intiallizing PRdownloader library
        val config: PRDownloaderConfig = PRDownloaderConfig.newBuilder()
            .setDatabaseEnabled(true)
            .setConnectTimeout(30000)
            .setReadTimeout(30000)
            .build()
        PRDownloader.initialize(applicationContext, config)



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
        mDeviceBandwidthSampler.startSampling()
        downLoadFile()
    }

    private fun downLoadFile() {
        webview_dwnld.setDownloadListener { url, userAgent, contentDescription, mimetype, contentLength ->

            /*Parsing the name from the url*/
            val fileName: String = URLUtil.guessFileName(url, contentDescription, mimetype)
            /*Downloaded File Path*/
            val dirPath: String = Environment.getExternalStorageDirectory().absolutePath
            pb_dwnlding.progress = 0
            /*Start of download progress by setting listeners
            **/
            downloadID = PRDownloader.download(url, dirPath, fileName)
                .build()
                .setOnStartOrResumeListener(object : OnStartOrResumeListener {
                    override fun onStartOrResume() {
                        Log.d("Check Error", "on Start Reached here")
                        tv_dwnldng_file_name.text = fileName
                    }
                })
                .setOnProgressListener(object : OnProgressListener {
                    override fun onProgress(progress: Progress?) {
                        iv_pause_resume.setImageResource(R.drawable.ic_pause)
                        //Converting progress into int value for the progress bar
                        val per =
                            (progress?.currentBytes!!.toFloat() / progress.totalBytes.toFloat()) * 100.00

                        //Showing the size of the file downloaded and the current file size
                        tv_file_size_total.text =
                            Utlis.getProgressDisplayLine(progress.currentBytes, progress.totalBytes)

                        //TO detect the speed of the downloads
                        val dec = java.text.DecimalFormat("####.##")
                        mConnectionClassManager.addBandwidth(
                            progress.totalBytes, SystemClock.currentThreadTimeMillis()
                        )
                         tv_spd_paus.text =
                             (dec.format(mConnectionClassManager.downloadKBitsPerSecond / 8000)).toString()

                        //Getting the progress to the ProgressBAr
                        pb_dwnlding.progress = per.toInt()
                    }
                })
                .setOnPauseListener(object : OnPauseListener {
                    override fun onPause() {
                        mDeviceBandwidthSampler.stopSampling()
                        iv_pause_resume.setImageResource(R.drawable.ic_play)
                        Log.d("Check Error", "ON PAUSE LISTENER Reached here")
                        Toast.makeText(this@MainActivity, "Download Paused", Toast.LENGTH_SHORT)
                            .show()
                    }
                })
                .setOnCancelListener(object : OnCancelListener {
                    override fun onCancel() {
                        mDeviceBandwidthSampler.stopSampling()
                        Log.d("Check Error", "ON CANCEL Reached here")
                        Toast.makeText(this@MainActivity, "Download Cancelled", Toast.LENGTH_SHORT)
                            .show()

                    }
                })
                .start(object : OnDownloadListener {
                    override fun onDownloadComplete() {
                        mDeviceBandwidthSampler.stopSampling()
                        Log.d("Check Error", "OnDOWNLOAD COMPLETE Reached here")
                        Toast.makeText(this@MainActivity, "File Downloaded", Toast.LENGTH_SHORT)
                            .show()
                    }

                    override fun onError(error: Error?) {
                        mDeviceBandwidthSampler.stopSampling()
                        Log.d("Check Error", "Error message ${error.toString()}")
                        Toast.makeText(this@MainActivity, "Error Occurred", Toast.LENGTH_SHORT)
                            .show()
                    }
                })
            /*
            * TO Start and pause the current download*/
            iv_pause_resume.setOnClickListener {
                if (PRDownloader.getStatus(downloadID) == Status.RUNNING) { //TO pause the downloaded current download
                    PRDownloader.pause(downloadID)
                } else if (PRDownloader.getStatus(downloadID) == Status.PAUSED) {  //TO Resume the current download
                    PRDownloader.resume(downloadID)
                }
            }
        }
    }

    override fun onResume() {
        if (Status.COMPLETED != PRDownloader.getStatus(downloadID) && Status.PAUSED == PRDownloader.getStatus(
                downloadID
            )
        ) {
            PRDownloader.resume(downloadID)
        }
        super.onResume()
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

