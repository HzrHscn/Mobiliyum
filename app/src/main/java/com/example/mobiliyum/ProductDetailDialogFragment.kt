package com.example.mobiliyum

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.example.mobiliyum.databinding.DialogProductDetailBinding // ViewBinding

class ProductDetailDialogFragment : DialogFragment() {

    private var _binding: DialogProductDetailBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_PRODUCT = "product_data"

        fun newInstance(product: Product): ProductDetailDialogFragment {
            val fragment = ProductDetailDialogFragment()
            val args = Bundle()
            args.putParcelable(ARG_PRODUCT, product)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogProductDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // API seviyesine göre güvenli Parcelable alma
        val product = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_PRODUCT, Product::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable(ARG_PRODUCT)
        }

        if (product == null) {
            dismiss()
            return
        }

        // ViewBinding ile verilere erişim
        binding.tvDialogName.text = product.name
        binding.tvDialogCategory.text = product.category
        binding.tvDialogPrice.text = product.price

        Glide.with(this)
            .load(product.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(binding.imgDialogProduct)

        binding.btnDialogGoStore.setOnClickListener {
            openWebsite(product.productUrl)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
            val height = ViewGroup.LayoutParams.WRAP_CONTENT
            window.setLayout(width, height)
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun openWebsite(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Link açılamadı: $url", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}