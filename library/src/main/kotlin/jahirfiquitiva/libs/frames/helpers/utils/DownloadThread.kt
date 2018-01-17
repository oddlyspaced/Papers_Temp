/*
 * Copyright (c) 2018. Jahir Fiquitiva
 *
 * Licensed under the CreativeCommons Attribution-ShareAlike
 * 4.0 International License. You may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *    http://creativecommons.org/licenses/by-sa/4.0/legalcode
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jahirfiquitiva.libs.frames.helpers.utils

import android.app.DownloadManager
import android.database.CursorIndexOutOfBoundsException
import android.os.Handler
import android.os.Looper
import jahirfiquitiva.libs.frames.ui.fragments.dialogs.WallpaperActionsDialog
import java.lang.ref.WeakReference

/**
 * Thanks to James Fenn
 */
class DownloadThread(
        frag: WallpaperActionsDialog,
        val listener: DownloadListener? = null
                    ) : Thread() {
    
    private var weakRef: WeakReference<WallpaperActionsDialog>? = null
    private var running = true
    private var progress: Int = 0
    
    init {
        weakRef = WeakReference(frag)
    }
    
    override fun run() {
        super.run()
        while (running) {
            val frag = weakRef?.get()
            if (frag == null) {
                listener?.onFailure(NullPointerException("Fragment was null"))
                running = false
            }
            frag?.let {
                val query = DownloadManager.Query()
                query.setFilterById(it.downloadId)
                
                val cursor = it.downloadManager?.query(query)
                cursor?.let {
                    it.moveToFirst()
                    try {
                        when (it.getInt(it.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
                            DownloadManager.STATUS_FAILED -> {
                                Handler(Looper.getMainLooper()).post {
                                    listener?.onFailure(Exception("Download manager failed"))
                                }
                                return
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                Handler(Looper.getMainLooper()).post { listener?.onSuccess() }
                                return
                            }
                            else -> {
                            }
                        }
                        
                        val downloaded = it.getInt(
                                it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = it.getInt(
                                it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        
                        progress = ((downloaded * 100L) / total).toInt()
                    } catch (e: CursorIndexOutOfBoundsException) {
                        FL.e { e.message }
                        
                        Handler(Looper.getMainLooper()).post {
                            val fileExists = frag.destFile?.exists() == true
                            if (fileExists) listener?.onSuccess()
                            else
                                listener?.onFailure(
                                        Exception("File was not downloaded successfully"))
                        }
                        return
                    }
                    
                    Handler(Looper.getMainLooper()).post {
                        listener?.onProgress(progress)
                    }
                    
                    it.close()
                }
            }
        }
    }
    
    fun cancel() {
        running = false
    }
    
    interface DownloadListener {
        fun onSuccess()
        fun onProgress(progress: Int)
        fun onFailure(exception: Exception)
    }
}