package com.sysdos.kasirpintar

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.sysdos.kasirpintar.data.model.Supplier
import com.sysdos.kasirpintar.viewmodel.ProductViewModel

class SupplierFragment : Fragment(R.layout.fragment_tab_supplier) {
    private lateinit var viewModel: ProductViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireActivity())[ProductViewModel::class.java]

        val lv = view.findViewById<ListView>(R.id.lvSupplier)
        val fab = view.findViewById<FloatingActionButton>(R.id.fabAddSupplier)

        viewModel.allSuppliers.observe(viewLifecycleOwner) { suppliers ->
            val list = suppliers.map { "🏢 ${it.name}\n📞 ${it.phone}\n📍 ${it.address}" }
            lv.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, list)

            // SAAT DIKLIK -> TAMPILKAN PILIHAN
            lv.setOnItemClickListener { _, _, pos, _ ->
                val selectedSupplier = suppliers[pos]

                val options = arrayOf("✏️ Edit Data", "🗑️ Hapus Supplier")
                AlertDialog.Builder(requireContext())
                    .setTitle(selectedSupplier.name)
                    .setItems(options) { _, which ->
                        if (which == 0) {
                            showSupplierDialog(selectedSupplier) // Edit
                        } else {
                            // HAPUS
                            AlertDialog.Builder(requireContext())
                                .setTitle("Hapus Supplier?")
                                .setMessage("Yakin hapus ${selectedSupplier.name}?")
                                .setPositiveButton("Ya, Hapus") { _, _ ->
                                    viewModel.deleteSupplier(selectedSupplier)
                                    Toast.makeText(context, "Supplier Dihapus", Toast.LENGTH_SHORT).show()
                                }
                                .setNegativeButton("Batal", null)
                                .show()
                        }
                    }
                    .show()
            }
        }
        fab.setOnClickListener { showSupplierDialog(null) }
    }

    private fun showSupplierDialog(supplierToEdit: Supplier?) {
        // ... (COPY LOGIKA DIALOG DARI SupplierActivity SEBELUMNYA KE SINI) ...
        // Agar tidak panjang, logic Add/Edit Supplier sama persis seperti sebelumnya
        // Ganti 'this' dengan 'requireContext()'

        val context = requireContext()
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val etName = EditText(context).apply { hint = "Nama Supplier"; setText(supplierToEdit?.name) }
        val etPhone = EditText(context).apply { hint = "No HP"; setText(supplierToEdit?.phone) }
        val etAddress = EditText(context).apply { hint = "Alamat"; setText(supplierToEdit?.address) }

        layout.addView(etName); layout.addView(etPhone); layout.addView(etAddress)

        AlertDialog.Builder(context)
            .setTitle(if(supplierToEdit==null) "Tambah" else "Edit")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                if (etName.text.isNotEmpty()) {
                    val s = Supplier(
                        id = supplierToEdit?.id ?: 0,
                        name = etName.text.toString(),
                        phone = etPhone.text.toString(),
                        address = etAddress.text.toString()
                    )
                    if (supplierToEdit == null) viewModel.insertSupplier(s) else viewModel.updateSupplier(s)
                }
            }
            .setNegativeButton("Batal", null).show()
    }
}