package com.example.calculator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calculator.CalculatorViewModel

@Composable
fun CalculatorScreen(
    onSecret: () -> Unit,
    viewModel: CalculatorViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.secretEvent.collect {
            onSecret()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = state.display,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (state.display.length > 12) 44.sp else 68.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CalcButton("C", Modifier.weight(1f), funcColor()) { viewModel.onButton("C") }
            CalcButton("⌫", Modifier.weight(1f), funcColor()) { viewModel.onButton("⌫") }
            CalcButton("%", Modifier.weight(1f), funcColor()) { viewModel.onButton("%") }
            CalcButton("÷", Modifier.weight(1f), operatorColor()) { viewModel.onButton("÷") }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CalcButton("7", Modifier.weight(1f), digitColor()) { viewModel.onButton("7") }
            CalcButton("8", Modifier.weight(1f), digitColor()) { viewModel.onButton("8") }
            CalcButton("9", Modifier.weight(1f), digitColor()) { viewModel.onButton("9") }
            CalcButton("×", Modifier.weight(1f), operatorColor()) { viewModel.onButton("×") }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CalcButton("4", Modifier.weight(1f), digitColor()) { viewModel.onButton("4") }
            CalcButton("5", Modifier.weight(1f), digitColor()) { viewModel.onButton("5") }
            CalcButton("6", Modifier.weight(1f), digitColor()) { viewModel.onButton("6") }
            CalcButton("−", Modifier.weight(1f), operatorColor()) { viewModel.onButton("−") }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CalcButton("1", Modifier.weight(1f), digitColor()) { viewModel.onButton("1") }
            CalcButton("2", Modifier.weight(1f), digitColor()) { viewModel.onButton("2") }
            CalcButton("3", Modifier.weight(1f), digitColor()) { viewModel.onButton("3") }
            CalcButton("+", Modifier.weight(1f), operatorColor()) { viewModel.onButton("+") }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CalcButton("0", Modifier.weight(2f), digitColor()) { viewModel.onButton("0") }
            CalcButton(".", Modifier.weight(1f), digitColor()) { viewModel.onButton(".") }
            CalcButton("=", Modifier.weight(1f), equalsColor()) { viewModel.onButton("=") }
        }
    }
}

@Composable
private fun CalcButton(
    label: String,
    modifier: Modifier = Modifier,
    background: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(5.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun digitColor(): Color = Color(0xFF1D232B)
private fun funcColor(): Color = Color(0xFF232A33)
private fun operatorColor(): Color = Color(0xFF2B3A55)
private fun equalsColor(): Color = Color(0xFF3E63DD)
