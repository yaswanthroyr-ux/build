package com.example.build.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.build.data.model.MealSummary
import com.example.build.data.repository.RecipeRepository
import com.example.build.ml.ImageLabelResult
import com.example.build.ml.ImageLabelingHelper
import com.example.build.ml.RecognitionResult
import com.example.build.ml.TextRecognitionHelper
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeScreen(
    user: FirebaseUser? = null,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { RecipeRepository() }
    val textRecognitionHelper = remember { TextRecognitionHelper(context) }
    val imageLabelingHelper = remember { ImageLabelingHelper() }

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessingMl by remember { mutableStateOf(false) }
    var textResult by remember { mutableStateOf<RecognitionResult?>(null) }
    var labelResult by remember { mutableStateOf<ImageLabelResult?>(null) }

    var searchIngredient by remember { mutableStateOf("") }
    var isSearchingRecipes by remember { mutableStateOf(false) }
    var mealList by remember { mutableStateOf<List<MealSummary>>(emptyList()) }
    var searchErrorMessage by remember { mutableStateOf<String?>(null) }

    var selectedMealIdForDetail by remember { mutableStateOf<String?>(null) }

    fun searchRecipesForIngredient(ingredient: String) {
        if (ingredient.isBlank()) return
        searchIngredient = ingredient
        isSearchingRecipes = true
        searchErrorMessage = null
        coroutineScope.launch {
            val result = repository.getMealsByIngredient(ingredient.trim())
            result.onSuccess { list ->
                mealList = list
                isSearchingRecipes = false
                if (list.isEmpty()) {
                    // Fallback search by meal name
                    val searchByNameResult = repository.searchMealsByName(ingredient.trim())
                    searchByNameResult.onSuccess { detailedList ->
                        mealList = detailedList.map {
                            MealSummary(idMeal = it.idMeal, strMeal = it.strMeal, strMealThumb = it.strMealThumb)
                        }
                    }
                }
            }.onFailure { error ->
                isSearchingRecipes = false
                searchErrorMessage = error.localizedMessage ?: "Failed to fetch recipes"
            }
        }
    }

    fun processBitmap(bitmap: Bitmap) {
        selectedBitmap = bitmap
        selectedUri = null
        isProcessingMl = true
        coroutineScope.launch {
            try {
                val textRes = textRecognitionHelper.processImage(bitmap)
                val labelRes = imageLabelingHelper.labelImage(bitmap)

                textResult = textRes
                labelResult = labelRes
                isProcessingMl = false

                // Auto search using best detected label or ingredient word
                val firstLabel = labelRes.labels.firstOrNull {
                    !listOf("food", "ingredient", "produce", "dish", "cuisine", "recipe", "plant").contains(it.lowercase())
                } ?: labelRes.labels.firstOrNull() ?: textRes.detectedIngredients.firstOrNull()

                firstLabel?.let { searchRecipesForIngredient(it) }
            } catch (e: Exception) {
                isProcessingMl = false
                Toast.makeText(context, "Recognition failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun processUri(uri: Uri) {
        selectedUri = uri
        selectedBitmap = null
        isProcessingMl = true
        coroutineScope.launch {
            try {
                val textRes = textRecognitionHelper.processImage(context, uri)
                val labelRes = imageLabelingHelper.labelImage(context, uri)

                textResult = textRes
                labelResult = labelRes
                isProcessingMl = false

                val firstLabel = labelRes.labels.firstOrNull {
                    !listOf("food", "ingredient", "produce", "dish", "cuisine", "recipe", "plant").contains(it.lowercase())
                } ?: labelRes.labels.firstOrNull() ?: textRes.detectedIngredients.firstOrNull()

                firstLabel?.let { searchRecipesForIngredient(it) }
            } catch (e: Exception) {
                isProcessingMl = false
                Toast.makeText(context, "Recognition failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            processBitmap(bitmap)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePhotoLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            processUri(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Recipe Finder", fontWeight = FontWeight.Bold)
                        Text(
                            text = user?.email ?: "Guest User",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Sign Out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Photograph Ingredient or Receipt",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Take a photo of an ingredient or receipt. ML Kit Image Labeling & Text Recognition will identify items and search recipes for you!",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        takePhotoLauncher.launch(null)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            ) {
                                Text("Take Photo")
                            }

                            OutlinedButton(
                                onClick = {
                                    galleryLauncher.launch("image/*")
                                }
                            ) {
                                Text("Pick Gallery")
                            }
                        }
                    }
                }
            }

            // Photo Preview
            if (selectedBitmap != null || selectedUri != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Captured / Selected Photo",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            selectedBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Selected Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            selectedUri?.let { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Selected Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }
                }
            }

            // ML Kit Processing Status & Results
            if (isProcessingMl) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Analyzing image with ML Kit...")
                    }
                }
            } else {
                if (labelResult != null || textResult != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "ML Kit Recognition Results",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // ML Kit Image Labeling Results
                                labelResult?.let { lRes ->
                                    if (lRes.labels.isNotEmpty()) {
                                        Text(
                                            text = "Ingredient Labels (Image Recognition):",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            lRes.labels.take(8).forEach { label ->
                                                AssistChip(
                                                    onClick = { searchRecipesForIngredient(label) },
                                                    label = { Text(label) }
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }

                                // ML Kit Text Recognition Results (Receipt Scanner)
                                textResult?.let { tRes ->
                                    if (tRes.amountsAndItems.isNotEmpty()) {
                                        Text(
                                            text = "Receipt Scanner - Amounts & Items Extracted:",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        tRes.amountsAndItems.forEach { (amount, item) ->
                                            Text(
                                                text = "• $item ($amount)",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    if (tRes.detectedIngredients.isNotEmpty()) {
                                        Text(
                                            text = "Text Words Detected (Tap to search):",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            tRes.detectedIngredients.take(10).forEach { word ->
                                                AssistChip(
                                                    onClick = { searchRecipesForIngredient(word) },
                                                    label = { Text(word) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Manual Search Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchIngredient,
                        onValueChange = { searchIngredient = it },
                        label = { Text("Search by Ingredient or Recipe Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { searchRecipesForIngredient(searchIngredient) }
                    ) {
                        Text("Search")
                    }
                }
            }

            // Recipe Results Header
            item {
                Text(
                    text = if (searchIngredient.isNotBlank()) "Recipes for \"$searchIngredient\"" else "Recipe Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isSearchingRecipes) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (searchErrorMessage != null) {
                item {
                    Text(
                        text = searchErrorMessage ?: "An error occurred",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else if (mealList.isEmpty()) {
                item {
                    Text(
                        text = "No recipes found. Try scanning another ingredient photo or typing an ingredient/recipe name above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(mealList) { meal ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMealIdForDetail = meal.idMeal },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = meal.strMealThumb,
                                contentDescription = meal.strMeal,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meal.strMeal,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap to view ingredients & instructions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog for Meal Details
    selectedMealIdForDetail?.let { id ->
        MealDetailDialog(
            mealId = id,
            repository = repository,
            onDismiss = { selectedMealIdForDetail = null }
        )
    }
}
