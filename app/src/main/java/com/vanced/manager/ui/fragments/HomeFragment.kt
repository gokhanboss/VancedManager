package com.vanced.manager.ui.fragments

import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.topjohnwu.superuser.Shell
import com.vanced.manager.R
import com.vanced.manager.adapter.*
import com.vanced.manager.core.downloader.MicrogDownloadService
import com.vanced.manager.core.downloader.VancedDownloadService
import com.vanced.manager.databinding.FragmentHomeBinding
import com.vanced.manager.ui.MainActivity
import com.vanced.manager.ui.dialogs.DialogContainer.installAlertBuilder
import com.vanced.manager.ui.dialogs.DialogContainer.launchVanced
import com.vanced.manager.ui.viewmodels.HomeViewModel
import com.vanced.manager.utils.AppUtils.installing
import com.vanced.manager.utils.PackageHelper

class HomeFragment : Fragment(), View.OnClickListener {

    private lateinit var binding: FragmentHomeBinding
    private var isExpanded: Boolean = false
    private val viewModel: HomeViewModel by viewModels()
    private val localBroadcastManager by lazy { LocalBroadcastManager.getInstance(requireActivity()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        requireActivity().title = getString(R.string.title_home)
        setHasOptionsMenu(true)
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_home, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewModel = viewModel

        val arg = "variant"
        val variant = getDefaultSharedPreferences(requireActivity()).getString("vanced_variant", "nonroot")

        with(binding) {
            when (variant) {
                "nonroot" -> includeMusicLayout.musicCard.visibility = View.GONE
                "music" -> includeVancedLayout.vancedCard.visibility = View.GONE
                "root" -> {
                    includeMusicLayout.musicCard.visibility = View.GONE
                    includeMicrogLayout.microgCard.visibility = View.GONE
                }
            }
        }

        with(binding) {
            includeVancedLayout.vancedInstallbtn.setOnClickListener(this@HomeFragment)
            includeVancedLayout.vancedUninstallbtn.setOnClickListener(this@HomeFragment)
            includeMicrogLayout.microgInstallbtn.setOnClickListener(this@HomeFragment)
            includeMicrogLayout.microgUninstallbtn.setOnClickListener(this@HomeFragment)
            includeChangelogsLayout.changelogButton.setOnClickListener(this@HomeFragment)
        }

        binding.includeVancedLayout.vancedCard.setOnLongClickListener {
            versionToast("Vanced", viewModel.vanced.get()?.getInstalledVersionName()!!)
            true
        }

        binding.includeMicrogLayout.microgCard.setOnLongClickListener {
            versionToast("MicroG", viewModel.microg.get()?.getInstalledVersionName()!!)
            true
        }

        with(binding.includeChangelogsLayout) {
            viewpager.adapter = if (variant == "root") SectionPageRootAdapter(this@HomeFragment) else SectionPageAdapter(this@HomeFragment)
            TabLayoutMediator(tablayout, viewpager) { tab, position ->
                if (variant == "root")
                    when (position) {
                        0 -> tab.text = "Vanced"
                        1 -> tab.text = "Manager"
                    }
                else
                    when (position) {
                        0 -> tab.text = "Vanced"
                        1 -> tab.text = "MicroG"
                        2 -> tab.text = "Manager"
                    }

            }.attach()
        }

    }

    override fun onClick(v: View?) {
        val prefs = requireActivity().getSharedPreferences("installPrefs", Context.MODE_PRIVATE)
        val variant = getDefaultSharedPreferences(requireActivity()).getString("vanced_variant", "nonroot")
        val vancedPkgName =
            if (variant == "root") {
                "com.google.android.youtube"
            } else {
                "com.vanced.android.youtube"
            }

        when (v?.id) {
            R.id.vanced_installbtn -> {
                if (!installing) {
                    if (!viewModel.fetching.get()!!) {
                        if (variant == "nonroot" && !viewModel.microg.get()?.isAppInstalled()!!) {
                            Snackbar.make(
                                binding.homeRefresh,
                                R.string.no_microg,
                                Snackbar.LENGTH_LONG
                            )
                                .setAction(R.string.install) {
                                    requireActivity().startService(
                                        Intent(
                                            requireActivity(),
                                            MicrogDownloadService::class.java
                                        )
                                    )
                                }.show()
                        } else {
                            if (prefs?.getBoolean("valuesModified", false)!!) {
                                requireActivity().startService(
                                    Intent(
                                        requireActivity(),
                                        VancedDownloadService::class.java
                                    )
                                )
                            } else {
                                view?.findNavController()?.navigate(R.id.toInstallThemeFragment)
                            }
                        }
                    }
                } else
                    Toast.makeText(requireActivity(), R.string.installation_wait, Toast.LENGTH_SHORT).show()
            }
            R.id.microg_installbtn -> {
                if (!installing)
                    requireActivity().startService(Intent(requireActivity(), MicrogDownloadService::class.java))
                else
                    Toast.makeText(requireActivity(), R.string.installation_wait, Toast.LENGTH_SHORT).show()
            }
            R.id.microg_uninstallbtn -> PackageHelper.uninstallApk("com.mgoogle.android.gms", requireActivity())
            R.id.vanced_uninstallbtn -> PackageHelper.uninstallApk(vancedPkgName, requireActivity())
            R.id.changelog_button -> cardExpandCollapse()
        }
    }

    private fun versionToast(name: String, app: String?) {
        val clip = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText(name, app))
        Toast.makeText(activity, getString(R.string.version_toast, name), Toast.LENGTH_LONG).show()
    }

    private fun cardExpandCollapse() {
        with(binding.includeChangelogsLayout) {
            viewpager.visibility = if (isExpanded) View.GONE else View.VISIBLE
            tablayout.visibility = if (isExpanded) View.GONE else View.VISIBLE
            changelogButton.animate().apply {
                rotation(if (isExpanded) 0F else 180F)
                interpolator = AccelerateDecelerateInterpolator()
            }
            isExpanded = !isExpanded
        }
    }

    override fun onPause() {
        localBroadcastManager.unregisterReceiver(broadcastReceiver)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        registerReceivers()
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                VANCED_DOWNLOADING -> {
                    with(binding.includeVancedLayout) {
                        vancedDownloading.visibility = View.VISIBLE
                        vancedDownloading.progress = intent.getIntExtra("progress", 0)
                        vancedDownloadingTxt.visibility = View.VISIBLE
                        vancedDownloadingTxt.text = requireActivity().getString(R.string.downloading_file, intent.getStringExtra("file"))
                    }
                }
                MICROG_DOWNLOADING -> {
                    with(binding.includeMicrogLayout) {
                        microgDownloading.visibility = View.VISIBLE
                        microgDownloading.progress = intent.getIntExtra("progress", 0)
                        microgDownloadingTxt.visibility = View.VISIBLE
                        microgDownloadingTxt.text = requireActivity().getString(R.string.downloading_file, "microg.apk")
                    }
                }
                MICROG_INSTALLING ->  {
                    with (binding.includeMicrogLayout) {
                        microgDownloading.visibility = View.GONE
                        microgDownloadingTxt.visibility = View.GONE
                        microgInstalling.visibility = View.VISIBLE
                    }
                }
                VANCED_INSTALLING -> {
                    with (binding.includeVancedLayout) {
                        vancedDownloading.visibility = View.GONE
                        vancedDownloadingTxt.visibility = View.GONE
                        vancedInstalling.visibility = View.VISIBLE
                    }
                }
                VANCED_INSTALLED -> {
                    binding.includeVancedLayout.vancedInstalling.visibility = View.GONE
                    launchVanced(requireActivity())
                    installing = false
                }
                MICROG_INSTALLED -> {
                    binding.includeMicrogLayout.microgInstalling.visibility = View.GONE
                    installing = false
                }
                INSTALL_FAILED -> {
                    with(binding) {
                        includeMicrogLayout.microgInstalling.visibility = View.GONE
                        includeVancedLayout.vancedInstalling.visibility = View.GONE
                    }
                    installAlertBuilder(intent.getStringExtra("errorMsg") as String, requireActivity())
                    installing = false
                }
                REFRESH_HOME -> {
                    Log.d("VMRefresh", "Refreshing home page")
                    viewModel.fetchData()
                }
            }
        }
    }

    private fun registerReceivers() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(VANCED_DOWNLOADING)
        intentFilter.addAction(MICROG_DOWNLOADING)
        intentFilter.addAction(VANCED_INSTALLING)
        intentFilter.addAction(MICROG_INSTALLING)
        intentFilter.addAction(VANCED_INSTALLED)
        intentFilter.addAction(MICROG_INSTALLED)
        intentFilter.addAction(REFRESH_HOME)
        intentFilter.addAction(INSTALL_FAILED)
        localBroadcastManager.registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.toolbar_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    companion object {
        const val VANCED_DOWNLOADING = "vanced_downloading"
        const val MICROG_DOWNLOADING = "microg_downloading"
        const val VANCED_INSTALLING = "vanced_installing"
        const val MICROG_INSTALLING = "microg_installing"
        const val VANCED_INSTALLED = "vanced_installed"
        const val MICROG_INSTALLED = "microg_installed"
        const val INSTALL_FAILED = "install_failed"
        const val REFRESH_HOME = "refresh_home"
    }
}

