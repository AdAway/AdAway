package org.adaway.ui.adware

class AdwareInstall(
    applicationName: String,
    packageName: String
) : HashMap<String, String>(2), Comparable<AdwareInstall> {
    init {
        put(APPLICATION_NAME_KEY, applicationName)
        put(PACKAGE_NAME_KEY, packageName)
    }

    override fun compareTo(other: AdwareInstall): Int {
        val nameComparison = get(APPLICATION_NAME_KEY).orEmpty()
            .compareTo(other[APPLICATION_NAME_KEY].orEmpty())
        return if (nameComparison == 0) {
            get(PACKAGE_NAME_KEY).orEmpty().compareTo(other[PACKAGE_NAME_KEY].orEmpty())
        } else {
            nameComparison
        }
    }

    companion object {
        const val APPLICATION_NAME_KEY = "app_name"
        const val PACKAGE_NAME_KEY = "package_name"
    }
}
