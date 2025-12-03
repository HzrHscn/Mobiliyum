package com.example.mobiliyum

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mobiliyum.databinding.FragmentMyReviewsBinding

class MyReviewsFragment : Fragment() {

    private var _binding: FragmentMyReviewsBinding? = null
    private val binding get() = _binding!!

    // Adapter'ı burada tanımlayıp lateinit yapabiliriz
    private lateinit var reviewAdapter: MyReviewsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyReviewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvMyReviews.layoutManager = LinearLayoutManager(context)

        // Adapter Kurulumu (Constructor boş olmalı)
        reviewAdapter = MyReviewsAdapter()
        binding.rvMyReviews.adapter = reviewAdapter

        loadReviews()
    }

    private fun loadReviews() {
        binding.progressBarReviews.visibility = View.VISIBLE
        binding.layoutEmptyReviews.visibility = View.GONE
        binding.rvMyReviews.visibility = View.GONE

        ReviewManager.getUserReviews { reviews ->
            binding.progressBarReviews.visibility = View.GONE

            if (reviews.isEmpty()) {
                binding.layoutEmptyReviews.visibility = View.VISIBLE
                binding.rvMyReviews.visibility = View.GONE
            } else {
                binding.layoutEmptyReviews.visibility = View.GONE
                binding.rvMyReviews.visibility = View.VISIBLE

                // DÜZELTME: Veriyi submitList ile gönderiyoruz
                reviewAdapter.submitList(reviews)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}