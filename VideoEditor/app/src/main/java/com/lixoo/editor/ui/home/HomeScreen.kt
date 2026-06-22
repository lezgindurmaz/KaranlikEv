package com.lixoo.editor.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lixoo.editor.data.local.entity.ProjectEntity
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToEditor: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val projects by viewModel.projects.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Lixoo Video Editor") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createNewProject(onNavigateToEditor) }) {
                Icon(Icons.Default.Add, contentDescription = "New Project")
            }
        }
    ) { padding ->
        if (projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Henüz proje yok. Yeni bir tane oluştur!")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(projects) { project ->
                    ProjectItem(project, onClick = { onNavigateToEditor(project.id) })
                }
            }
        }
    }
}

@Composable
fun ProjectItem(project: ProjectEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = project.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Son düzenleme: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(project.lastModifiedAt)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
