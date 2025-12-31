package com.sysdos.kasirpintar

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProductEntryActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private var currentPhotoPath: String? = null
    private var productToEdit: Product? = null

    // UI Elements
    private lateinit var etName: TextInputEditText
    private lateinit var etPrice: TextInputEditText
    private lateinit var etCost: TextInputEditText // Input Harga Modal (HPP)
    private lateinit var etStock: TextInputEditText
    private lateinit var etBarcode: TextInputEditText

    // Khusus Kategori tipe-nya AutoCompleteTextView (Dropdown)
    private lateinit var etCategory: AutoCompleteTextView

    private lateinit var ivProduct: ImageView

    // --- LAUNCHER KAMERA ---
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setPic()
        }
    }

    // --- LAUNCHER SCANNER ---
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            etBarcode.setText(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_entry)

        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // 1. BINDING VIEW (Hubungkan dengan ID di XML)
        etName = findViewById(R.id.etProductName)
        etPrice = findViewById(R.id.etProductPrice)
        etCost = findViewById(R.id.etProductCost)
        etStock = findViewById(R.id.etProductStock)
        etBarcode = findViewById(R.id.etProductBarcode)
        ivProduct = findViewById(R.id.ivProductImage)

        // Setup Dropdown Kategori
        etCategory = findViewById(R.id.etProductCategory)
        setupCategoryDropdown()

        val btnPhoto = findViewById<Button>(R.id.btnTakePhoto)
        val btnScan = findViewById<Button>(R.id.btnScanBarcode)
        val btnSave = findViewById<Button>(R.id.btnSaveProduct)

        // 2. CEK MODE: Apakah ini Edit Produk atau Tambah Baru?
        if (intent.hasExtra("PRODUCT_TO_EDIT")) {
            productToEdit = intent.getParcelableExtra("PRODUCT_TO_EDIT")
            productToEdit?.let {
                etName.setText(it.name)
                etPrice.setText(it.price.toInt().toString())
                etCost.setText(it.costPrice.toInt().toString()) // Load Modal
                etStock.setText(it.stock.toString())
                etCategory.setText(it.category, false) // false agar dropdown gak langsung muncul
                etBarcode.setText(it.barcode)

                currentPhotoPath = it.imagePath
                if (currentPhotoPath != null) setPic()

                btnSave.text = "UPDATE PRODUK"
            }
        }

        // 3. LISTENERS (Tombol ditekan)

        // Tombol Foto
        btnPhoto.setOnClickListener { checkCameraPermissionAndOpen() }

        // Tombol Scan
        btnScan.setOnClickListener {
            val options = ScanOptions()
            options.setPrompt("Scan Barcode Produk")
            options.setBeepEnabled(true)
            options.setOrientationLocked(true)
            options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity::class.java)
            barcodeLauncher.launch(options)
        }

        // Tombol Simpan
        btnSave.setOnClickListener {
            saveProduct()
        }
    }

    private fun setupCategoryDropdown() {
        val categories = arrayOf(
            "Makanan",
            "Minuman",
            "Snack / Camilan",
            "Sembako",
            "Rokok",
            "Obat-obatan",
            "ATK",
            "Lainnya"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        etCategory.setAdapter(adapter)
    }

    private fun saveProduct() {
        val name = etName.text.toString()
        val priceStr = etPrice.text.toString()
        val costStr = etCost.text.toString()
        val stockStr = etStock.text.toString()
        val category = etCategory.text.toString()
        val barcode = etBarcode.text.toString()

        // Validasi Wajib Isi
        if (name.isEmpty() || priceStr.isEmpty() || stockStr.isEmpty()) {
            Toast.makeText(this, "Nama, Harga, dan Stok wajib diisi!", Toast.LENGTH_SHORT).show()
            return
        }

        val price = priceStr.toDouble()
        val cost = if (costStr.isNotEmpty()) costStr.toDouble() else 0.0
        val stock = stockStr.toInt()

        // Validasi Bisnis: Cek Laba/Rugi
        if (price < cost) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Potensi Rugi")
                .setMessage("Harga Jual (Rp ${price.toInt()}) lebih murah dari Modal (Rp ${cost.toInt()}).\n\nYakin tetap simpan?")
                .setPositiveButton("Ya, Simpan") { _, _ ->
                    // Lanjut Simpan
                    processSave(name, price, cost, stock, category, barcode)
                }
                .setNegativeButton("Perbaiki Harga", null)
                .show()
            return
        }

        // Jika aman, langsung simpan
        processSave(name, price, cost, stock, category, barcode)
    }

    private fun processSave(name: String, price: Double, cost: Double, stock: Int, category: String, barcode: String) {
        val newProduct = Product(
            id = productToEdit?.id ?: 0, // 0 = Auto Generate ID Baru
            name = name,
            price = price,
            costPrice = cost,
            stock = stock,
            category = category,
            barcode = barcode.ifEmpty { null },
            imagePath = currentPhotoPath
        )

        if (productToEdit == null) {
            viewModel.insert(newProduct)
            Toast.makeText(this, "✅ Produk Baru Disimpan!", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.update(newProduct)
            Toast.makeText(this, "✅ Produk Diupdate!", Toast.LENGTH_SHORT).show()
        }
        finish() // Kembali ke layar sebelumnya
    }

    // --- FUNGSI KAMERA (FileProvider) ---
    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        } else {
            dispatchTakePictureIntent()
        }
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Buat file kosong dulu
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: Exception) {
                    Toast.makeText(this, "Gagal membuat file foto", Toast.LENGTH_SHORT).show()
                    null
                }
                // Jika file berhasil dibuat, buka kamera
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "${packageName}.provider", // Pastikan sesuai dengan AndroidManifest
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    takePictureLauncher.launch(takePictureIntent)
                }
            }
        }
    }

    private fun createImageFile(): File {
        // Buat nama file unik berdasarkan waktu
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun setPic() {
        // Tampilkan foto ke ImageView
        currentPhotoPath?.let { path ->
            val bitmap = BitmapFactory.decodeFile(path)
            ivProduct.setImageBitmap(bitmap)
        }
    }
}