package com.abhiiscoding.kidsdrawingapp

import android.Manifest
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null // A variable for current color is picked from color pallet.

    var customProgressDialog: Dialog? = null

    val openGalleryLauncher:ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        result->
           if (result.resultCode== RESULT_OK && result.data != null){
              val imageBackground: ImageView = findViewById(R.id.iv_Background)
               imageBackground.setImageURI(result.data?.data)
           }
    }

    /** Todo 2: create an ActivityResultLauncher with MultiplePermissions since we are requesting
     * both read and write
     */
    val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val perMissionName = it.key
                val isGranted = it.value
                //Todo 3: if permission is granted show a toast and perform operation
                if (isGranted ) {
                    Toast.makeText(
                        this@MainActivity,
                        "Permission granted, Add an image from the gallery.",
                        Toast.LENGTH_LONG
                    ).show()
                    val pickIntent = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)
                } else {
                    //Todo 4: Displaying another toast if permission is not granted and this time focus on
                    //    Read external storage
                    if (perMissionName == Manifest.permission.READ_EXTERNAL_STORAGE)
                        Toast.makeText(
                            this@MainActivity,
                            "Oops! you just denied the permission.",
                            Toast.LENGTH_LONG
                        ).show()
                }
            }

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawing_view)
        val ibBrush: ImageButton = findViewById(R.id.ib_brush)
        val ibUndo: ImageButton = findViewById(R.id.ibUndoBtn)
        val ibRedo: ImageButton = findViewById(R.id.ibRedoBtn)
        val ibSave: ImageButton = findViewById(R.id.ibSaveBtn)
        val ibGallery: ImageButton = findViewById(R.id.ibGalleryBtn)
        drawingView?.setSizeForBrush(20.toFloat())
        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.paintColors)
        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton
        mImageButtonCurrentPaint?.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.pallete_pressed
            )
        )
        ibBrush.setOnClickListener {
            showBrushSizeChooserDialog()
        }
        ibUndo.setOnClickListener {
            drawingView?.onClickUndo()
        }
        ibRedo.setOnClickListener {
            drawingView?.onClickRedo()
        }
        ibSave.setOnClickListener {
            showProgressDialog()
            if (isReadStorageAllowed()){
                lifecycleScope.launch {
                    val flDrawingView: FrameLayout = findViewById(R.id.flDrawingViewContainer)
                    saveBitmapFile(getBitmapFromView(flDrawingView))
                }
            }
        }
        //TODO(Step 10 : Adding an click event to image button for selecting the image from gallery.)

        ibGallery.setOnClickListener {
            requestStoragePermission()
        }

    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size :")
        val smallBtn: ImageButton = brushDialog.findViewById(R.id.ibSmallBrush)
        smallBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        })
        val mediumBtn: ImageButton = brushDialog.findViewById(R.id.ibMediumBrush)
        mediumBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        })

        val largeBtn: ImageButton = brushDialog.findViewById(R.id.ibLargeBrush )
        largeBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        })
        val VerylargeBtn: ImageButton = brushDialog.findViewById(R.id.ibVeryLargeBrush )
        VerylargeBtn.setOnClickListener(View.OnClickListener {
            drawingView?.setSizeForBrush(50.toFloat())
            brushDialog.dismiss()
        })
        brushDialog.show()
    }

    fun paintClicked(view: View) {
        if (view !== mImageButtonCurrentPaint) {
            // Update the color
            val imageButton = view as ImageButton
            // Here the tag is used for swapping the current color with previous color.
            // The tag stores the selected view
            val colorTag = imageButton.tag.toString()
            // The color is set as per the selected tag here.
            drawingView?.setColor(colorTag)
            // Swap the backgrounds for last active and currently active image button.
            imageButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallete_pressed))
            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.pallete_normal
                )
            )

            //Current view is updated with selected view in the form of ImageButton.
            mImageButtonCurrentPaint = view
        }
    }

    private fun isReadStorageAllowed():Boolean{
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
        return result== PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_MEDIA_IMAGES)){
            showRationaleDialog("Permission required","Kids Drawing App " +
                    "needs to Access Your Images, Go to settings and give permission.")
        }
        else {
            requestPermission.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }

    }

    private fun showRationaleDialog(title: String, message: String,
    ) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun getBitmapFromView(view:View):Bitmap{
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)
        return returnedBitmap
    }

//    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String{
//        var result = ""
//        withContext(Dispatchers.IO){
//            if (mBitmap!=null){
//                try {
//                    val bytes = ByteArrayOutputStream()
//                    mBitmap.compress(Bitmap.CompressFormat.PNG,90,bytes)
//                    val f = File(externalCacheDir?.absoluteFile.toString()
//                                + File.separator + "KidsDrawingApp_" + System.currentTimeMillis() / 1000 + ".jpg")
//                    val fo = FileOutputStream(f)
//                    fo.write(bytes.toByteArray())
//
//                    fo.close()
//
//                    result = f.absolutePath
//                    runOnUiThread{
//                        cancelProgressDialog()
//                        if (result.isNotEmpty()){
//                            Toast.makeText(this@MainActivity, "File saved in Gallery", Toast.LENGTH_SHORT).show()
//                        } else {
//                            Toast.makeText(
//                                this@MainActivity,
//                                "Something went wrong while saving the file.",
//                                Toast.LENGTH_SHORT
//                            ).show()
//                        }
//                    }
//                }
//                catch (e: Exception){
//                    result = ""
//                    e.printStackTrace()
//                }
//            }
//        }
//        return result
//    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String {
        var result = ""
        withContext(Dispatchers.IO) {
            if (mBitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes)
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(
                        Date()
                    )
                    val imageFileName = "KidsDrawingApp_$timeStamp.jpg"

                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, imageFileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
                    }

                    val resolver = applicationContext.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                    uri?.let {
                        val outputStream = resolver.openOutputStream(it)
                        outputStream?.use { stream ->
                            bytes.writeTo(stream)
                            result = it.toString()
                        }
                    }

                    runOnUiThread {
                        cancelProgressDialog()
                        if (result.isNotEmpty()) {
                            Toast.makeText(this@MainActivity, "File saved in Gallery", Toast.LENGTH_SHORT).show()
//                            shareImage(result)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Something went wrong while saving the file.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showProgressDialog(){
        customProgressDialog = Dialog(this@MainActivity)
         customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }
    private fun cancelProgressDialog(){
        if (customProgressDialog!=null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }


//    private fun shareImage(result:String){
//        MediaScannerConnection.scanFile(this, arrayOf(result), null){
//            path, uri->
//                 val shareIntent = Intent()
//            shareIntent.action = Intent.ACTION_SEND
//            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
//            shareIntent.type = "image/png"
//            startActivity(Intent.createChooser(shareIntent, "Share"))
//        }
//    }

    private fun shareImage(result:String){
        val uri = FileProvider.getUriForFile(this, "com.abhiiscoding.kidsdrawingapp.fileprovider", File(result))
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.type = "image/png"
        startActivity(Intent.createChooser(shareIntent, "Share"))
    }

    fun eraserClicked(view: View) {
        val imageButton = view as ImageButton
        val colorTag = imageButton.tag.toString()
        drawingView?.setColor(colorTag)
//        mImageButtonCurrentPaint = view
    }


}