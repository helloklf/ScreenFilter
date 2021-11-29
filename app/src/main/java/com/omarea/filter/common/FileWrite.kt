package com.omarea.shared

import android.content.Context
import android.content.res.AssetManager
import android.os.Environment
import android.provider.ContactsContract.Directory.PACKAGE_NAME
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * 提供公共方法，向外置存储读写文件
 * Created by helloklf on 2016/8/27.
 */
object FileWrite {
    val SDCardDir: String = Environment.getExternalStorageDirectory().absolutePath
    var baseUrl = "${SDCardDir}/Android/data/${PACKAGE_NAME}/"

    fun writeFile(ass: AssetManager, file: String, hasExtName: Boolean): String? {
        try {
            val inputStream = ass.open(file)
            val datas = ByteArray(2 * 1024 * 1024)
            val len = inputStream.read(datas)

            /*
            //获取SD卡的目录
            File sdCardDir = Environment.getExternalStorageDirectory();
            File targetFile = new File(sdCardDir.getCanonicalPath() + "shelltoolsfile.zip");
            //以指定文件创建RandomAccessFile对象
            RandomAccessFile raf = new RandomAccessFile(targetFile, "rw");
            //将文件记录指针移动到最后
            raf.seek(targetFile.length());
            //输出文件内容
            raf.write(datas,0,len);
            raf.close();
            */
            val dir = File(FileWrite.baseUrl)
            if (!dir.exists())
                dir.mkdirs()
            val filePath = FileWrite.baseUrl + if (hasExtName)
                file
            else
                file.substring(0, if (file.lastIndexOf(".") > 0) file.lastIndexOf(".") else file.length)

            val fileOutputStream = FileOutputStream(filePath)
            fileOutputStream.write(datas, 0, len)
            fileOutputStream.close()
            inputStream.close()
            val writedFile = File(filePath)
            writedFile.setWritable(true)
            writedFile.setExecutable(true)
            writedFile.setReadable(true)
            //getApplicationContext().getClassLoader().getResourceAsStream("");
            return filePath
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun writeFile(assetManager: AssetManager, file: String, outName: String): String? {
        try {
            val inputStream = assetManager.open(file)
            val datas = ByteArray(2 * 1024 * 1024)
            //inputStream.available()
            val len = inputStream.read(datas)
            val dir = File(FileWrite.baseUrl)
            if (!dir.exists())
                dir.mkdirs()
            val filePath = FileWrite.baseUrl + outName
            val fileDir = File(filePath).parentFile
            if (!fileDir.exists())
                fileDir.mkdirs()

            val fileOutputStream = FileOutputStream(filePath)
            fileOutputStream.write(datas, 0, len)
            fileOutputStream.close()
            inputStream.close()
            val writedFile = File(filePath)
            writedFile.setWritable(true)
            writedFile.setExecutable(true)
            writedFile.setReadable(true)
            //getApplicationContext().getClassLoader().getResourceAsStream("");
            return filePath
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun getPrivateFileDir(context: Context): String {
        return context.filesDir.path + "/private/"
    }

    fun getPrivateFilePath(context: Context, outName: String): String {
        return getPrivateFileDir(context) + (if (outName.startsWith("/")) outName.substring(1, outName.length) else outName)
    }

    fun writePrivateFile(assetManager: AssetManager, file: String, outName: String, context: Context): String? {
        try {
            val inputStream = assetManager.open(file)
            val datas = ByteArray(2 * 1024 * 1024)
            //inputStream.available()
            val len = inputStream.read(datas)
            val dir = File(getPrivateFileDir(context))
            if (!dir.exists())
                dir.mkdirs()
            val filePath = getPrivateFilePath(context, outName)
            val fileDir = File(filePath).parentFile
            if (!fileDir.exists())
                fileDir.mkdirs()

            val fileOutputStream = FileOutputStream(filePath)
            fileOutputStream.write(datas, 0, len)
            fileOutputStream.close()
            inputStream.close()
            val writedFile = File(filePath)
            writedFile.setWritable(true)
            writedFile.setExecutable(true)
            writedFile.setReadable(true)
            return filePath
            //getApplicationContext().getClassLoader().getResourceAsStream("");
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun writePrivateFile(bytes: ByteArray, outName: String, context: Context): Boolean {
        try {
            val dir = File(getPrivateFileDir(context))
            if (!dir.exists())
                dir.mkdirs()
            val filePath = getPrivateFilePath(context, outName)
            val fileDir = File(filePath).parentFile
            if (!fileDir.exists())
                fileDir.mkdirs()

            val fileOutputStream = FileOutputStream(filePath)
            fileOutputStream.write(bytes, 0, bytes.size)
            fileOutputStream.close()
            File(filePath).setExecutable(true, false)
            //getApplicationContext().getClassLoader().getResourceAsStream("");
            val writedFile = File(filePath)
            writedFile.setWritable(true)
            writedFile.setExecutable(true)
            writedFile.setReadable(true)
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

    fun writePrivateShellFile(file: String, outName: String, context: Context): String? {
        val data = parseText(context, file)
        if (data.size > 0 && FileWrite.writePrivateFile(data, outName, context)) {
            return getPrivateFilePath(context, outName)
        }
        return null
    }

    //Dos转Unix，避免\r\n导致的脚本无法解析
    private fun parseText(context: Context, fileName: String): ByteArray {
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open(fileName)
            val datas = ByteArray(2 * 1024 * 1024)
            //inputStream.available()
            val len = inputStream.read(datas)
            val codes = String(datas, 0, len).replace(Regex("\r\n"), "\n").replace(Regex("\r\t"), "\t")
            return codes.toByteArray(Charsets.UTF_8)
        } catch (ex: Exception) {
            ex.message?.let { Log.e("script-parse", it) }
            return "".toByteArray()
        }
    }
}
