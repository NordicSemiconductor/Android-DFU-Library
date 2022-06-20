/*
 * Copyright (c) 2022, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list
 * of conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.dfu.profile.main.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DFUFileManager @Inject constructor(
    @ApplicationContext
    private val context: Context
) {

    private val TAG = "DFU_FILE_MANAGER"

    fun createFile(uri: Uri): ZipFile? {
        return try {
            createFromFile(uri)
        } catch (e: Exception) {
            try {
                createFromContentResolver(uri)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading file from content resolver.", e)
                null
            }
        }
    }

    private fun createFromFile(uri: Uri): ZipFile {
        val file = uri.toFile()
        return ZipFile(uri, file.name, file.path, file.length())
    }

    private fun createFromContentResolver(uri: Uri): ZipFile? {
        val data = context.contentResolver.query(uri, null, null, null, null)

        return if (data != null && data.moveToNext()) {

            val displayNameIndex = data.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val fileSizeIndex = data.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val dataIndex = data.getColumnIndex(MediaStore.MediaColumns.DATA)

            val fileName = data.getString(displayNameIndex)
            val fileSize = data.getInt(fileSizeIndex)
            val filePath = if (dataIndex != -1) {
                data.getString(dataIndex)
            } else {
                null
            }

            data.close()

            ZipFile(uri, fileName, filePath, fileSize.toLong())
        } else {
            Log.d(TAG, "Data loaded from ContentResolver is empty.")
            null
        }
    }
}
