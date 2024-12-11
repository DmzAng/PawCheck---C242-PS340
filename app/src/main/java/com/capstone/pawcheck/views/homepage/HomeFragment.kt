    package com.capstone.pawcheck.views.homepage

    import android.os.Bundle
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.Toast
    import androidx.fragment.app.Fragment
    import androidx.fragment.app.viewModels
    import androidx.navigation.fragment.findNavController
    import androidx.recyclerview.widget.LinearLayoutManager
    import com.capstone.pawcheck.adapter.ArticleAdapter
    import com.capstone.pawcheck.data.di.Injection
    import com.capstone.pawcheck.R
    import com.capstone.pawcheck.databinding.FragmentHomeBinding
    import com.capstone.pawcheck.viewmodel.HomeViewModel
    import com.capstone.pawcheck.viewmodel.ViewModelFactory

    class HomeFragment : Fragment() {

        private var _binding: FragmentHomeBinding? = null
        private val binding get() = _binding!!

        private val homeViewModel: HomeViewModel by viewModels {
            ViewModelFactory(Injection.provideRepository())
        }

        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            _binding = FragmentHomeBinding.inflate(inflater, container, false)
            return binding.root
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            binding.recyclerView.apply {
                layoutManager = LinearLayoutManager(requireContext())
            }

            homeViewModel.articles.observe(viewLifecycleOwner) { response ->
                val articles = response.listStory?.filterNotNull() ?: emptyList()
                val adapter = ArticleAdapter(articles)
                binding.recyclerView.adapter = adapter
            }

            homeViewModel.error.observe(viewLifecycleOwner) { errorMessage ->
                Toast.makeText(requireContext(), "Error: $errorMessage", Toast.LENGTH_SHORT).show()
            }

            homeViewModel.fetchArticles()

            binding.btnCamFrag.setOnClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_cameraFragment)
            }
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }
    }
