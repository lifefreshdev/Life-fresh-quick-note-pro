package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.data.database.LeadEntity
import com.example.ui.viewmodel.CRMViewModel
import com.example.leads.domain.LeadDraft
import com.example.leads.domain.LeadField
import com.example.leads.domain.LeadValidator
import org.json.JSONArray
import java.util.*
import kotlinx.coroutines.launch

private val WELLNESS_CATEGORY_ALIASES = mapOf(
    "heart problem" to "Heart Disease",
    "tuberculosis (tb)" to "Tuberculosis",
    "tb" to "Tuberculosis",
    "uric acid" to "High Uric Acid",
    "uric acid high" to "High Uric Acid"
)

/**
 * Full searchable wellness library from the Free version.
 *
 * Duplicate/legacy labels are normalized before this public list is built,
 * so new records use one stable canonical value.
 */
val DISEASE_OPTIONS = listOf(
    "Abdominal Pain",
    "Acidity",
    "Acne",
    "ACL Injury",
    "ADHD",
    "Allergy",
    "Alopecia",
    "Alzheimer's Disease",
    "Amebiasis",
    "Anemia",
    "Ankylosing Spondylitis",
    "Anxiety",
    "Appendix Pain",
    "Arthritis",
    "Asthma",
    "Autism",
    "Back Pain",
    "Bell's Palsy",
    "Bipolar Disorder",
    "Bronchitis",
    "Calcium Deficiency",
    "Cataract",
    "Cervical Spondylosis",
    "Chickenpox",
    "Chikungunya",
    "Cholesterol",
    "Chronic Fatigue Syndrome",
    "Cold & Cough",
    "Conjunctivitis",
    "Constipation",
    "COPD",
    "Dandruff",
    "Dehydration",
    "Dengue",
    "Dental Pain",
    "Depression",
    "Dermatitis",
    "Detoxification",
    "Diabetes",
    "Diarrhea",
    "Digestive Issue",
    "Dry Eyes",
    "Dysentery",
    "Dysmenorrhea (Painful Periods)",
    "Ear Infection",
    "Eczema",
    "Epilepsy",
    "Eye Allergy",
    "Fatigue",
    "Fatty Liver",
    "Fever",
    "Fibromyalgia",
    "Fissure",
    "Fistula",
    "Food Poisoning",
    "Frozen Shoulder",
    "Fungal Infection",
    "Gallbladder Stone",
    "Gastric Problem",
    "Gastritis",
    "Gastroenteritis",
    "GERD / Acid Reflux",
    "Glaucoma",
    "Gout",
    "Gum Disease",
    "Hair Fall",
    "Headache",
    "Heart Blockage",
    "Heart Disease",
    "Heel Pain",
    "Hepatitis",
    "Hernia",
    "High BP",
    "Hyperthyroidism",
    "Hypothyroidism",
    "IBS",
    "Immunity Building",
    "Indigestion",
    "Infertility",
    "Insomnia",
    "Iron Deficiency",
    "Jaundice",
    "Joint Pain",
    "Kidney Disease",
    "Kidney Stone",
    "Knee Pain",
    "Lactose Intolerance",
    "Ligament Injury",
    "Ligament Tear",
    "Liver Disease",
    "Low BP",
    "Lumbar Spondylosis",
    "Malaria",
    "Meniscus Injury",
    "Menopause",
    "Menstrual Irregularity",
    "Migraine",
    "Mouth Ulcer",
    "Muscle Cramps",
    "Nasal Congestion",
    "Neck Pain",
    "Neuropathy",
    "Obesity",
    "OCD",
    "Osteoarthritis",
    "Osteoporosis",
    "Otitis Media",
    "Ovarian Cyst",
    "Panic Disorder",
    "Pancreatitis",
    "Paralysis",
    "Parkinson's Disease",
    "PCOD",
    "PCOS",
    "Pharyngitis",
    "Piles",
    "Plantar Fasciitis",
    "Pneumonia",
    "Posture Correction",
    "Pregnancy Care",
    "Prostate Enlargement (BPH)",
    "Psoriasis",
    "Rheumatoid Arthritis",
    "Ringworm",
    "Sciatica",
    "Scoliosis",
    "Shoulder Pain",
    "Sinus",
    "Skin Allergy",
    "Sleep Problem",
    "Slip Disc",
    "Sore Throat",
    "Spondylolisthesis",
    "Spondylosis",
    "Sports Injury",
    "Sprain",
    "Stroke",
    "Stroke Rehabilitation",
    "Stress",
    "Tendonitis",
    "Tennis Elbow",
    "Throat Infection",
    "Thyroid",
    "Tonsillitis",
    "Tuberculosis",
    "Typhoid",
    "High Uric Acid",
    "Urinary Incontinence",
    "Urticaria",
    "UTI",
    "Uterine Fibroids",
    "Varicose Veins",
    "Vertigo",
    "Viral Fever",
    "Vitiligo",
    "Vitamin B12 Deficiency",
    "Vitamin D Deficiency",
    "Vitamin Deficiency",
    "Weakness",
    "Weight Gain",
    "Weight Loss",
    "Worms",
    "Other"
)

private fun normalizeWellnessCategory(value: String): String {
    val clean = value.trim().replace(Regex("\\s+"), " ")
    if (clean.isEmpty()) return ""

    return WELLNESS_CATEGORY_ALIASES[clean.lowercase(Locale.US)] ?: clean
}

private fun normalizeWellnessCategories(
    values: Iterable<String>
): List<String> {
    val normalized = LinkedHashMap<String, String>()

    values.forEach { rawValue ->
        val canonical = normalizeWellnessCategory(rawValue)
        if (canonical.isNotEmpty()) {
            normalized.putIfAbsent(
                canonical.lowercase(Locale.US),
                canonical
            )
        }
    }

    return normalized.values.toList()
}

private fun wellnessCategoryMatches(
    category: String,
    rawQuery: String
): Boolean {
    val query = rawQuery.trim().lowercase(Locale.US)
    if (query.isEmpty()) return true

    if (category.lowercase(Locale.US).contains(query)) return true

    return WELLNESS_CATEGORY_ALIASES.any { (alias, canonical) ->
        canonical.equals(category, ignoreCase = true) &&
            alias.contains(query)
    }
}

private fun highlightedCategoryName(
    category: String,
    rawQuery: String,
    highlightColor: Color,
    primaryColor: Color
): AnnotatedString {
    val query = rawQuery.trim()
    if (query.isEmpty()) return AnnotatedString(category)

    val normalizedCategory = category.lowercase(Locale.US)
    val normalizedQuery = query.lowercase(Locale.US)
    val firstMatch = normalizedCategory.indexOf(normalizedQuery)

    if (firstMatch < 0) return AnnotatedString(category)

    return AnnotatedString.Builder().apply {
        append(category.substring(0, firstMatch))
        pushStyle(
            SpanStyle(
                background = highlightColor,
                color = primaryColor,
                fontWeight = FontWeight.Bold
            )
        )
        append(
            category.substring(
                firstMatch,
                firstMatch + query.length
            )
        )
        pop()
        append(category.substring(firstMatch + query.length))
    }.toAnnotatedString()
}

val RELATION_OPTIONS = listOf(
    "Self", "Father", "Mother", "grand mother (nani)", "grand father (nana)",
    "grand mother (dadi)", "grand father (dada)", "Brother", "Sister",
    "Husband", "Wife", "Son", "Daughter", "Relative", "Friend", "Other"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LeadFormDialog(
    lead: LeadEntity?, // Null for add, Non-null for edit
    viewModel: CRMViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // Fields
    var name by rememberSaveable { mutableStateOf(lead?.name ?: "") }
    var mobile by rememberSaveable { mutableStateOf(lead?.mobile ?: "") }
    
    // Initial disease selection from lead entity (multi-select list values)
    val initialDiseases = remember(lead) {
        if (lead != null) {
            try {
                val array = JSONArray(lead.diseases)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                normalizeWellnessCategories(list)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    var checkedDiseases by rememberSaveable {
        mutableStateOf(initialDiseases)
    }
    
    var showDiseaseDialog by rememberSaveable { mutableStateOf(false) }
    
    var otherDisease by rememberSaveable { mutableStateOf(lead?.otherDisease ?: "") }
    
    // Relation States
    var relation by rememberSaveable { mutableStateOf(lead?.relation ?: "") }
    var relationExpanded by rememberSaveable { mutableStateOf(false) }
    var otherRelation by rememberSaveable { mutableStateOf(lead?.otherRelation ?: "") }
    
    // Optional Reminder States
    var reminderDate by rememberSaveable { mutableStateOf(lead?.reminderDate ?: "") }
    var reminderTime by rememberSaveable { mutableStateOf(lead?.reminderTime ?: "") }
    var reminderNote by rememberSaveable { mutableStateOf(lead?.reminderNote ?: "") }
    
    // CRM Properties
    var status by rememberSaveable { mutableStateOf(lead?.status ?: "Pending") }
    var notes by rememberSaveable { mutableStateOf(lead?.notes ?: "") }

    // Validation inline error states
    var nameError by remember { mutableStateOf<String?>(null) }
    var mobileError by remember { mutableStateOf<String?>(null) }
    var diseaseError by remember { mutableStateOf<String?>(null) }
    var otherDiseaseError by remember { mutableStateOf<String?>(null) }
    var relationError by remember { mutableStateOf<String?>(null) }
    var otherRelationError by remember { mutableStateOf<String?>(null) }
    var reminderError by remember { mutableStateOf<String?>(null) }

    // Saving and Tap-prevention State
    var isSaving by remember { mutableStateOf(false) }

    // Dialog setup
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                it.setStatusBarColor(android.graphics.Color.TRANSPARENT)
                it.setNavigationBarColor(android.graphics.Color.TRANSPARENT)
                it.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .testTag("lead_form_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Header (App Bar replacement - Premium styling)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Go Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column {
                            Text(
                                text = if (lead == null) "Add Client" else "Edit Client",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (lead == null) "Create a new client profile." else "Update client information.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("lead_form_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Form",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Scrollable Form Fields
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    
                    // SECTION 1: Client Information
                    FormSectionHeader(title = "Client Information", icon = Icons.Default.Person)

                    // Name
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PremiumFilledTextField(
                            value = name,
                            onValueChange = { 
                                name = it 
                                if (it.trim().isNotEmpty()) nameError = null
                            },
                            label = "Name *",
                            placeholder = "Enter full name",
                            leadingIcon = Icons.Default.Person,
                            isError = nameError != null,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "lead_form_name_field"
                        )
                        nameError?.let { InlineErrorText(it) }
                    }

                    // Mobile
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PremiumFilledTextField(
                            value = mobile,
                            onValueChange = { 
                                mobile = it 
                                if (it.trim().isNotEmpty()) mobileError = null
                            },
                            label = "Mobile Number *",
                            placeholder = "e.g. 9876543210",
                            leadingIcon = Icons.Default.Phone,
                            isError = mobileError != null,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "lead_form_mobile_field"
                        )
                        mobileError?.let { InlineErrorText(it) }
                    }

                    // Disease Category trigger (Beautiful interactive trigger box)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDiseaseDialog = true }
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (diseaseError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("lead_form_disease_category_field")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = if (diseaseError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = "Disease Category *",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (diseaseError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (checkedDiseases.isEmpty()) "Select Wellness Issues" else "${checkedDiseases.size} selected",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (checkedDiseases.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Diseases",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Elegant Selected Disease Chips below field
                        if (checkedDiseases.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                checkedDiseases.forEach { disease ->
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                            .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                            .clickable { checkedDiseases = checkedDiseases - disease }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = disease,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 13.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        diseaseError?.let { InlineErrorText(it) }
                    }

                    // Searchable wellness selector using the Pro dialog visual language.
                    if (showDiseaseDialog) {
                        var searchQuery by remember { mutableStateOf("") }
                        var tempCheckedDiseases by remember {
                            mutableStateOf(
                                normalizeWellnessCategories(checkedDiseases).toSet()
                            )
                        }

                        val filteredDiseases = remember(searchQuery) {
                            DISEASE_OPTIONS.filter { category ->
                                wellnessCategoryMatches(category, searchQuery)
                            }
                        }

                        AlertDialog(
                            onDismissRequest = { showDiseaseDialog = false },
                            shape = RoundedCornerShape(24.dp),
                            title = {
                                Column(
                                    verticalArrangement =
                                        Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Wellness Category",
                                        style =
                                            MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color =
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text =
                                            "Search and select all issues that apply to this client.",
                                        style =
                                            MaterialTheme.typography.bodySmall,
                                        color =
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 430.dp),
                                    verticalArrangement =
                                        Arrangement.spacedBy(10.dp)
                                ) {
                                    PremiumFilledTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        label = "Search Wellness Categories",
                                        placeholder =
                                            "e.g. thyroid, weakness, back pain",
                                        leadingIcon = Icons.Default.Search,
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(
                                                    onClick = {
                                                        searchQuery = ""
                                                    },
                                                    modifier = Modifier.testTag(
                                                        "clear_wellness_search"
                                                    )
                                                ) {
                                                    Icon(
                                                        imageVector =
                                                            Icons.Default.Close,
                                                        contentDescription =
                                                            "Clear Search"
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        testTag = "wellness_search_input"
                                    )

                                    Text(
                                        text =
                                            "${tempCheckedDiseases.size} selected • " +
                                                "${filteredDiseases.size} shown",
                                        style =
                                            MaterialTheme.typography.labelMedium,
                                        color =
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement =
                                            Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(
                                            items = filteredDiseases,
                                            key = { it }
                                        ) { disease ->
                                            val isChecked =
                                                tempCheckedDiseases.contains(
                                                    disease
                                                )
                                            val highlightedName =
                                                highlightedCategoryName(
                                                    category = disease,
                                                    rawQuery = searchQuery,
                                                    highlightColor =
                                                        MaterialTheme.colorScheme
                                                            .primaryContainer
                                                            .copy(alpha = 0.55f),
                                                    primaryColor =
                                                        MaterialTheme.colorScheme
                                                            .primary
                                                )

                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        tempCheckedDiseases =
                                                            if (isChecked) {
                                                                tempCheckedDiseases -
                                                                    disease
                                                            } else {
                                                                tempCheckedDiseases +
                                                                    disease
                                                            }
                                                    }
                                                    .testTag(
                                                        "disease_checkbox_row_${
                                                            disease.replace(
                                                                " ",
                                                                "_"
                                                            )
                                                        }"
                                                    ),
                                                shape =
                                                    RoundedCornerShape(16.dp),
                                                color = if (isChecked) {
                                                    MaterialTheme.colorScheme
                                                        .primaryContainer
                                                        .copy(alpha = 0.32f)
                                                } else {
                                                    MaterialTheme.colorScheme
                                                        .surfaceVariant
                                                        .copy(alpha = 0.08f)
                                                },
                                                border = BorderStroke(
                                                    width =
                                                        if (isChecked) {
                                                            1.5.dp
                                                        } else {
                                                            1.dp
                                                        },
                                                    color = if (isChecked) {
                                                        MaterialTheme.colorScheme
                                                            .primary
                                                            .copy(alpha = 0.65f)
                                                    } else {
                                                        MaterialTheme.colorScheme
                                                            .outlineVariant
                                                            .copy(alpha = 0.3f)
                                                    }
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            horizontal = 12.dp,
                                                            vertical = 8.dp
                                                        ),
                                                    verticalAlignment =
                                                        Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = isChecked,
                                                        onCheckedChange = {
                                                            checked ->
                                                            tempCheckedDiseases =
                                                                if (
                                                                    checked ==
                                                                    true
                                                                ) {
                                                                    tempCheckedDiseases +
                                                                        disease
                                                                } else {
                                                                    tempCheckedDiseases -
                                                                        disease
                                                                }
                                                        },
                                                        modifier =
                                                            Modifier.testTag(
                                                                "disease_checkbox_${
                                                                    disease.replace(
                                                                        " ",
                                                                        "_"
                                                                    )
                                                                }"
                                                            )
                                                    )
                                                    Spacer(
                                                        modifier =
                                                            Modifier.width(8.dp)
                                                    )
                                                    Text(
                                                        text = highlightedName,
                                                        style =
                                                            MaterialTheme.typography
                                                                .bodyMedium,
                                                        fontWeight =
                                                            if (isChecked) {
                                                                FontWeight.Bold
                                                            } else {
                                                                FontWeight.Medium
                                                            },
                                                        color =
                                                            MaterialTheme.colorScheme
                                                                .onSurface,
                                                        modifier =
                                                            Modifier.weight(1f)
                                                    )
                                                    if (isChecked) {
                                                        Icon(
                                                            imageVector =
                                                                Icons.Default.Check,
                                                            contentDescription =
                                                                "Selected",
                                                            tint =
                                                                MaterialTheme.colorScheme
                                                                    .primary,
                                                            modifier =
                                                                Modifier.size(
                                                                    18.dp
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (filteredDiseases.isEmpty()) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            vertical = 28.dp
                                                        ),
                                                    contentAlignment =
                                                        Alignment.Center
                                                ) {
                                                    Text(
                                                        text =
                                                            "No wellness categories match your search.",
                                                        style =
                                                            MaterialTheme.typography
                                                                .bodyMedium,
                                                        color =
                                                            MaterialTheme.colorScheme
                                                                .onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        checkedDiseases =
                                            normalizeWellnessCategories(
                                                tempCheckedDiseases
                                            )
                                        if (checkedDiseases.isNotEmpty()) {
                                            diseaseError = null
                                        }
                                        showDiseaseDialog = false
                                    },
                                    modifier = Modifier.testTag(
                                        "dialog_disease_done_button"
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Text("Done")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showDiseaseDialog = false
                                    },
                                    modifier = Modifier.testTag(
                                        "cancel_disease_dialog"
                                    )
                                ) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    // Specify Other Disease
                    if (checkedDiseases.contains("Other")) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PremiumFilledTextField(
                                value = otherDisease,
                                onValueChange = { 
                                    otherDisease = it 
                                    if (it.trim().isNotEmpty()) otherDiseaseError = null
                                },
                                label = "Specify Other Disease *",
                                placeholder = "Specify wellness issue",
                                leadingIcon = Icons.Default.Notes,
                                isError = otherDiseaseError != null,
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "lead_form_other_disease_field"
                            )
                            otherDiseaseError?.let { InlineErrorText(it) }
                        }
                    }

                    // Client Relation
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = relationExpanded,
                            onExpandedChange = { relationExpanded = !relationExpanded }
                        ) {
                            PremiumFilledTextField(
                                value = relation,
                                onValueChange = {},
                                label = "Client Relation *",
                                placeholder = "Select relation",
                                leadingIcon = Icons.Default.People,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = relationExpanded) },
                                isError = relationError != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                testTag = "lead_form_relation_field"
                            )
                            ExposedDropdownMenu(
                                expanded = relationExpanded,
                                onDismissRequest = { relationExpanded = false }
                            ) {
                                RELATION_OPTIONS.forEach { r ->
                                    DropdownMenuItem(
                                        text = { Text(r, style = MaterialTheme.typography.bodyMedium) },
                                        onClick = {
                                            relation = r
                                            relationError = null
                                            relationExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        relationError?.let { InlineErrorText(it) }
                    }

                    // Specify Other Relation
                    if (relation == "Other") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PremiumFilledTextField(
                                value = otherRelation,
                                onValueChange = { 
                                    otherRelation = it 
                                    if (it.trim().isNotEmpty()) otherRelationError = null
                                },
                                label = "Specify Other Relation *",
                                placeholder = "Specify client relation",
                                leadingIcon = Icons.Default.Notes,
                                isError = otherRelationError != null,
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "lead_form_other_relation_field"
                            )
                            otherRelationError?.let { InlineErrorText(it) }
                        }
                    }

                    // SECTION 2: Reminder Information
                    FormSectionHeader(title = "Reminder Information", icon = Icons.Default.Notifications)

                    // Set Reminder Block (High-end scheduler design)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Set Reminder (Optional)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Date Picker trigger (Sleek button layout)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            val c = Calendar.getInstance()
                                            DatePickerDialog(
                                                context,
                                                { _, y, m, d ->
                                                    reminderDate = String.format(Locale.US, "%d-%02d-%02d", y, m + 1, d)
                                                    reminderError = null
                                                },
                                                c.get(Calendar.YEAR),
                                                c.get(Calendar.MONTH),
                                                c.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = reminderDate.ifEmpty { "Date" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (reminderDate.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                                                color = if (reminderDate.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (reminderDate.isNotEmpty()) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear Date",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable {
                                                        reminderDate = ""
                                                        reminderError = null
                                                    }
                                            )
                                        }
                                    }
                                }

                                // Time Picker trigger
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            val parts = reminderTime.split(":")
                                            val initialHour: Int
                                            val initialMinute: Int
                                            if (parts.size == 2) {
                                                initialHour = parts[0].toIntOrNull() ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                                                initialMinute = parts[1].toIntOrNull() ?: Calendar.getInstance().get(Calendar.MINUTE)
                                            } else {
                                                val c = Calendar.getInstance()
                                                initialHour = c.get(Calendar.HOUR_OF_DAY)
                                                initialMinute = c.get(Calendar.MINUTE)
                                            }
                                            TimePickerDialog(
                                                context,
                                                { _, h, min ->
                                                    reminderTime = String.format(Locale.US, "%02d:%02d", h, min)
                                                    reminderError = null
                                                },
                                                initialHour,
                                                initialMinute,
                                                false
                                            ).show()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (reminderTime.isEmpty()) "Time" else formatTime12Hour(reminderTime),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (reminderTime.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                                                color = if (reminderTime.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (reminderTime.isNotEmpty()) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear Time",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable {
                                                        reminderTime = ""
                                                        reminderError = null
                                                    }
                                            )
                                        }
                                    }
                                }
                            }

                            // Reminder Note Input
                            PremiumFilledTextField(
                                value = reminderNote,
                                onValueChange = { reminderNote = it },
                                label = "Reminder Note",
                                placeholder = "e.g. Wellness Coaching Session",
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "lead_form_reminder_note"
                            )

                            reminderError?.let { InlineErrorText(it) }
                        }
                    }

                    // SECTION 3: Additional Notes & Status
                    FormSectionHeader(title = "Status & Coaching Notes", icon = Icons.Default.Assignment)

                    // Client Status Group (Beautiful Premium Chips)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Status *",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatusChip(
                                label = "Pending",
                                selected = (status == "Pending"),
                                selectedColor = Color(0xFFFB8C00), // Amber
                                icon = Icons.Default.Schedule,
                                modifier = Modifier.weight(1f),
                                onClick = { status = "Pending" },
                                testTag = "status_pending"
                            )
                            StatusChip(
                                label = "Completed",
                                selected = (status == "Complete"),
                                selectedColor = Color(0xFF43A047), // Green
                                icon = Icons.Default.CheckCircle,
                                modifier = Modifier.weight(1f),
                                onClick = { status = "Complete" },
                                testTag = "status_complete"
                            )
                        }
                    }

                    // Quick Notes
                    PremiumFilledTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = "Quick Notes",
                        placeholder = "Add any notes about this wellness client...",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        testTag = "lead_form_notes_field"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons scroll with the form, matching the Free version.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                if (isSaving) return@Button

                                // Reset error states
                                nameError = null
                                mobileError = null
                                diseaseError = null
                                otherDiseaseError = null
                                relationError = null
                                otherRelationError = null
                                reminderError = null

                                val normalizedWellnessCategories =
                                    normalizeWellnessCategories(checkedDiseases)

                                checkedDiseases =
                                    normalizedWellnessCategories

                                val validationResult = LeadValidator.validate(
                                    LeadDraft(
                                        id = lead?.id,
                                        name = name,
                                        mobile = mobile,
                                        diseases = normalizedWellnessCategories,
                                        otherDisease = otherDisease,
                                        relation = relation,
                                        otherRelation = otherRelation,
                                        status = status,
                                        reminderDate = reminderDate,
                                        reminderTime = reminderTime,
                                        reminderNote = reminderNote,
                                        notes = notes
                                    )
                                )

                                val issues = validationResult.issues
                                if (issues.isNotEmpty()) {
                                    for (issue in issues) {
                                        when (issue.field) {
                                            LeadField.NAME -> nameError = issue.message
                                            LeadField.MOBILE -> mobileError = issue.message
                                            LeadField.DISEASES -> diseaseError = issue.message
                                            LeadField.OTHER_DISEASE -> otherDiseaseError = issue.message
                                            LeadField.RELATION -> relationError = issue.message
                                            LeadField.OTHER_RELATION -> otherRelationError = issue.message
                                            LeadField.REMINDER_DATE, LeadField.REMINDER_TIME -> {
                                                if (reminderError == null) {
                                                    reminderError = issue.message
                                                }
                                            }
                                            else -> { /* STATUS handled below */ }
                                        }
                                    }
                                    val statusIssue = validationResult.firstIssueFor(LeadField.STATUS)
                                    val toastMsg = statusIssue?.message ?: "Please correct the highlighted errors."
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val normalizedDraft = validationResult.normalizedDraft

                                // Proceed to save
                                isSaving = true
                                focusManager.clearFocus()

                                coroutineScope.launch {
                                    try {
                                        val result = viewModel.saveLead(
                                            id = normalizedDraft.id,
                                            name = normalizedDraft.name,
                                            mobile = normalizedDraft.mobile,
                                            diseases = normalizedDraft.diseases,
                                            otherDisease = normalizedDraft.otherDisease,
                                            relation = normalizedDraft.relation,
                                            otherRelation = normalizedDraft.otherRelation,
                                            status = normalizedDraft.status,
                                            reminderDate = normalizedDraft.reminderDate,
                                            reminderTime = normalizedDraft.reminderTime,
                                            reminderNote = normalizedDraft.reminderNote,
                                            notes = normalizedDraft.notes
                                        )

                                        when (result) {
                                            com.example.ui.viewmodel.CRMViewModel.SaveLeadResult.SUCCESS -> {
                                                if (normalizedDraft.reminderDate.isNotBlank()) {
                                                    viewModel.triggerExactAlarmPrompt()
                                                }
                                                Toast.makeText(
                                                    context,
                                                    if (lead == null) {
                                                        "Client added successfully!"
                                                    } else {
                                                        "Client updated successfully!"
                                                    },
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onDismiss()
                                            }

                                            com.example.ui.viewmodel.CRMViewModel.SaveLeadResult.DUPLICATE_MOBILE -> {
                                                mobileError =
                                                    "This mobile number already exists in the system."
                                                Toast.makeText(
                                                    context,
                                                    "Validation Error: Duplicate mobile number.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }

                                            com.example.ui.viewmodel.CRMViewModel.SaveLeadResult.DUPLICATE_REMINDER -> {
                                                reminderError =
                                                    "A reminder already exists at the selected date and time."
                                                Toast.makeText(
                                                    context,
                                                    "Conflict: Select another reminder time.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }

                                            else -> {
                                                Toast.makeText(
                                                    context,
                                                    "Client could not be saved. Please retry.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(50.dp)
                                .testTag("lead_form_save_button"),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = if (lead == null) "Add Client" else "Update Client",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Spacer(
                        modifier = Modifier.windowInsetsBottomHeight(
                            WindowInsets.navigationBars
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun FormSectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun InlineErrorText(error: String) {
    AnimatedVisibility(
        visible = error.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatusChip(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) selectedColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) selectedColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumFilledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) { { Text(placeholder) } } else null,
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)) } },
        trailingIcon = trailingIcon,
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.04f),
            errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            errorLabelColor = MaterialTheme.colorScheme.error
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
            .testTag(testTag)
            .border(
                width = 1.dp,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(16.dp)
            )
    )
}

private fun formatTime12Hour(timeStr: String): String {
    if (timeStr.isEmpty()) return ""
    return try {
        val parser = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
        val date = parser.parse(timeStr) ?: return timeStr
        val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
        formatter.format(date).uppercase(java.util.Locale.US)
    } catch (e: Exception) {
        timeStr
    }
}
