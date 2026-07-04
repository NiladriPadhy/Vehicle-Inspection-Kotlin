package com.vsp.inspection.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vsp.core.model.Section
import com.vsp.inspection.feature.auth.LoginScreen
import com.vsp.inspection.feature.auth.SignUpScreen
import com.vsp.inspection.feature.capture.CaptureScreen
import com.vsp.inspection.feature.data.DataScreen
import com.vsp.inspection.feature.checklist.ChecklistHubScreen
import com.vsp.inspection.feature.checklist.ChecklistSectionScreen
import com.vsp.inspection.feature.checklist.SectionCaptureScreen
import com.vsp.inspection.feature.dashboard.DashboardScreen
import com.vsp.inspection.feature.identify.IdentifyScreen
import com.vsp.inspection.feature.imagedetail.ImageDetailScreen
import com.vsp.inspection.feature.olddocs.OldVehicleDocsScreen
import com.vsp.inspection.feature.report.ReportScreen
import com.vsp.inspection.feature.review.ReviewScreen
import com.vsp.inspection.feature.start.StartInspectionScreen
import com.vsp.inspection.feature.verification.VerificationScreen

/** Root navigation host wiring the full inspection wizard (US1–US9). */
@Composable
fun VspNavHost(navController: NavHostController = rememberNavController()) {
    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = VspRoute.Login,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<VspRoute.Login> {
                LoginScreen(
                    onSignedIn = {
                        navController.navigate(VspRoute.Dashboard) {
                            popUpTo(VspRoute.Login) { inclusive = true }
                        }
                    },
                    onCreateAccount = { navController.navigate(VspRoute.SignUp) },
                )
            }

            composable<VspRoute.SignUp> {
                SignUpScreen(
                    onSignedIn = {
                        navController.navigate(VspRoute.Dashboard) {
                            popUpTo(VspRoute.Login) { inclusive = true }
                        }
                    },
                    onBackToLogin = { navController.popBackStack() },
                )
            }

            composable<VspRoute.Dashboard> {
                DashboardScreen(
                    onNewInspection = { navController.navigate(VspRoute.StartInspection) },
                    onResume = { navController.navigate(VspRoute.IdentifyVehicle(it.id)) },
                    onOpenChecklist = { navController.navigate(VspRoute.ChecklistHub(it.id)) },
                    onOpenData = { navController.navigate(VspRoute.DataManagement) },
                    onSignedOut = {
                        navController.navigate(VspRoute.Login) {
                            popUpTo(VspRoute.Dashboard) { inclusive = true }
                        }
                    },
                )
            }

            composable<VspRoute.DataManagement> {
                DataScreen(onBack = { navController.popBackStack() })
            }

            composable<VspRoute.StartInspection> {
                StartInspectionScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { id ->
                        navController.navigate(VspRoute.IdentifyVehicle(id)) {
                            popUpTo(VspRoute.StartInspection) { inclusive = true }
                        }
                    },
                )
            }

            composable<VspRoute.IdentifyVehicle> { entry ->
                val id = entry.toRoute<VspRoute.IdentifyVehicle>().inspectionId
                IdentifyScreen(
                    onBack = { navController.popBackStack() },
                    // Both new and old vehicles go straight to the checklist; document photos
                    // (RC, insurance, etc.) are captured within the Documents checklist section.
                    onContinue = { _ ->
                        navController.navigate(VspRoute.ChecklistHub(id))
                    },
                )
            }

            composable<VspRoute.OldVehicleDocs> { entry ->
                val id = entry.toRoute<VspRoute.OldVehicleDocs>().inspectionId
                OldVehicleDocsScreen(
                    onBack = { navController.popBackStack() },
                    onContinue = {
                        navController.navigate(VspRoute.ChecklistHub(id)) {
                            launchSingleTop = true
                            popUpTo(VspRoute.OldVehicleDocs(id)) { inclusive = true }
                        }
                    },
                )
            }

            composable<VspRoute.ExteriorCapture> { entry ->
                val id = entry.toRoute<VspRoute.ExteriorCapture>().inspectionId
                CaptureScreen(
                    inspectionId = id,
                    section = Section.EXTERIOR,
                    onBack = { navController.popBackStack() },
                    onSectionComplete = { navController.popBackStack() },
                )
            }

            composable<VspRoute.InteriorCapture> { entry ->
                val id = entry.toRoute<VspRoute.InteriorCapture>().inspectionId
                CaptureScreen(
                    inspectionId = id,
                    section = Section.INTERIOR,
                    onBack = { navController.popBackStack() },
                    onSectionComplete = { navController.popBackStack() },
                )
            }

            composable<VspRoute.Review> { entry ->
                val id = entry.toRoute<VspRoute.Review>().inspectionId
                ReviewScreen(
                    onBack = { navController.popBackStack() },
                    onOpenImage = { imageId -> navController.navigate(VspRoute.ImageDetail(imageId)) },
                    onOpenChecklist = { navController.navigate(VspRoute.ChecklistHub(id)) },
                    onFinalize = { navController.navigate(VspRoute.FinalVerification(id)) },
                )
            }

            composable<VspRoute.ChecklistHub> { entry ->
                val id = entry.toRoute<VspRoute.ChecklistHub>().inspectionId
                ChecklistHubScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSection = { sectionId ->
                        navController.navigate(VspRoute.ChecklistSection(id, sectionId))
                    },
                    onContinue = { navController.navigate(VspRoute.Review(id)) },
                )
            }

            composable<VspRoute.ChecklistSection> { entry ->
                val route = entry.toRoute<VspRoute.ChecklistSection>()
                val id = route.inspectionId
                val sectionEnum = when (route.sectionId) {
                    "documents" -> Section.DOCUMENT
                    "exterior", "wheels", "underbody", "engine_bay" -> Section.EXTERIOR
                    else -> Section.INTERIOR
                }
                ChecklistSectionScreen(
                    onBack = { navController.popBackStack() },
                    onCaptureItem = { itemId ->
                        navController.navigate(
                            VspRoute.SectionCapture(id, route.sectionId, sectionEnum.name, itemId),
                        )
                    },
                    onOpenImage = { imageId, itemId ->
                        navController.navigate(VspRoute.ImageDetail(imageId, route.sectionId, itemId))
                    },
                )
            }

            composable<VspRoute.SectionCapture> {
                SectionCaptureScreen(onExit = { navController.popBackStack() })
            }

            composable<VspRoute.ImageDetail> {
                ImageDetailScreen(onBack = { navController.popBackStack() })
            }

            composable<VspRoute.FinalVerification> { entry ->
                val id = entry.toRoute<VspRoute.FinalVerification>().inspectionId
                VerificationScreen(
                    onBack = { navController.popBackStack() },
                    onFinalized = { navController.navigate(VspRoute.Report(id)) },
                )
            }

            composable<VspRoute.Report> {
                ReportScreen(
                    onBack = { navController.popBackStack() },
                    onDone = {
                        navController.navigate(VspRoute.Dashboard) {
                            popUpTo(VspRoute.Dashboard) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
