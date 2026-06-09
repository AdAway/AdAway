package org.adaway.ui.compose

import androidx.compose.material3.ComposeMaterial3Flags
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun enableMaterial3ExpressiveFlags() {
    ComposeMaterial3Flags.isCheckboxStylingFixEnabled = true
    ComposeMaterial3Flags.isSnackbarStylingFixEnabled = true
    ComposeMaterial3Flags.isPrecisionPointerComponentSizingEnabled = true
    ComposeMaterial3Flags.isAnchoredDraggableComponentsStrictOffsetCheckEnabled = true
    ComposeMaterial3Flags.isAnchoredDraggableComponentsInvalidationFixEnabled = true
    ComposeMaterial3Flags.isBottomSheetPartiallyExpandedDeterministicEnabled = true
}
