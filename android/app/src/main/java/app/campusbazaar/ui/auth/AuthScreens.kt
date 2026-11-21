package app.campusbazaar.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.campusbazaar.BuildConfig
import app.campusbazaar.R
import app.campusbazaar.data.repo.AuthRepository
import app.campusbazaar.domain.InputRules
import app.campusbazaar.ui.appViewModel
import app.campusbazaar.ui.components.Field
import app.campusbazaar.ui.components.PriceTag
import app.campusbazaar.ui.components.PrimaryButton
import app.campusbazaar.ui.components.QuietButton
import app.campusbazaar.ui.components.SecondaryButton
import app.campusbazaar.ui.components.StatusStamp
import app.campusbazaar.ui.components.TagSize
import app.campusbazaar.ui.theme.CB
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(onSignUp: () -> Unit, onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(CB.Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_brand_mark), contentDescription = null, modifier = Modifier.height(30.dp))
            Spacer(Modifier.width(10.dp))
            Text("CampusBazaar", style = MaterialTheme.typography.titleLarge, color = CB.Ink)
        }
        // The hero sits centred in the space above the buttons instead of leaving a gap below it.
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            // The single bold moment: tags and stamps from a campus notice board.
            Box(Modifier.fillMaxWidth().height(170.dp)) {
                Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    WelcomeTagRow("Casio fx-991EX", 650)
                    WelcomeTagRow("Engineering Drawing, N.D. Bhatt", 280)
                    WelcomeTagRow("Hercules cycle, 26 inch", 2400)
                }
                StatusStamp("SOLD", Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 4.dp), large = true)
            }
            Spacer(Modifier.height(28.dp))
            Text("Buy and sell with students on your campus", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "Old books, calculators, cycles, coolers and notes. Chat, make an offer, and meet at a safe spot on campus.",
                style = MaterialTheme.typography.bodyLarge,
                color = CB.InkSoft,
            )
        }
        PrimaryButton("Create account", onSignUp, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        SecondaryButton("I already have an account", onSignIn, Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Text(
            buildAnnotatedString {
                val link = TextLinkStyles(SpanStyle(color = CB.Ink, textDecoration = TextDecoration.Underline))
                append("By continuing you agree to the ")
                withLink(LinkAnnotation.Url("${BuildConfig.API_BASE_URL}/terms", link)) { append("terms of use") }
                append(" and ")
                withLink(LinkAnnotation.Url("${BuildConfig.API_BASE_URL}/privacy", link)) { append("privacy policy") }
                append(".")
            },
            style = MaterialTheme.typography.bodySmall,
            color = CB.InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WelcomeTagRow(title: String, price: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PriceTag(price, size = TagSize.Medium)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, color = CB.Ink)
    }
}

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {
    data class State(val loading: Boolean = false, val error: String? = null)
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun signIn(email: String, password: String) = run { repo.signIn(email, password) }
    fun signUp(name: String, email: String, password: String) = run { repo.signUp(name, email, password) }

    private fun run(block: suspend () -> Result<Unit>) {
        if (_state.value.loading) return
        _state.value = State(loading = true)
        viewModelScope.launch {
            block().onFailure { _state.value = State(error = it.message) }.onSuccess { _state.value = State() }
        }
    }
}

@Composable
private fun AuthFrame(title: String, subtitle: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(CB.Paper).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft)
        Spacer(Modifier.height(28.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, error: String?, label: String = "Password", supporting: String? = null) {
    var visible by rememberSaveable { mutableStateOf(false) }
    Field(
        value = value,
        onValueChange = onChange,
        label = label,
        error = error,
        supporting = supporting,
        keyboardType = KeyboardType.Password,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (visible) "Hide password" else "Show password")
            }
        },
        maxLength = 128,
    )
}

@Composable
private fun FormError(message: String?) {
    if (message == null) return
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = CB.Stamp,
        modifier = Modifier.fillMaxWidth().background(CB.StampWash, RoundedCornerShape(8.dp)).padding(12.dp),
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
fun SignInScreen(onBack: () -> Unit, onCreateAccount: () -> Unit) {
    val vm = appViewModel { c, _ -> AuthViewModel(c.auth) }
    val state by vm.state.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val emailError = if (submitted) InputRules.emailError(email) else null
    val passwordError = if (submitted && password.isEmpty()) "Enter your password." else null

    AuthFrame("Welcome back", "Sign in to pick up your chats and listings.", onBack) {
        FormError(state.error)
        Field(email, { email = it.trim() }, "Email", error = emailError, keyboardType = KeyboardType.Email, maxLength = 254)
        Spacer(Modifier.height(16.dp))
        PasswordField(password, { password = it }, passwordError)
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Sign in", {
            submitted = true
            if (InputRules.emailError(email) == null && password.isNotEmpty()) vm.signIn(email, password)
        }, Modifier.fillMaxWidth(), loading = state.loading)
        Spacer(Modifier.height(8.dp))
        QuietButton("New here? Create an account", onCreateAccount, Modifier.fillMaxWidth())
    }
}

@Composable
fun SignUpScreen(onBack: () -> Unit, onSignIn: () -> Unit) {
    val vm = appViewModel { c, _ -> AuthViewModel(c.auth) }
    val state by vm.state.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val nameError = if (submitted) InputRules.nameError(name) else null
    val emailError = if (submitted) InputRules.emailError(email) else null
    val passwordError = if (submitted) InputRules.passwordError(password) else null

    AuthFrame("Create your account", "Use the name other students will recognise. You'll pick your campus next.", onBack) {
        FormError(state.error)
        Field(name, { name = it }, "Full name", error = nameError, placeholder = "Asha Rao", maxLength = 60)
        Spacer(Modifier.height(16.dp))
        Field(email, { email = it.trim() }, "Email", error = emailError, keyboardType = KeyboardType.Email, placeholder = "you@college.edu", maxLength = 254)
        Spacer(Modifier.height(16.dp))
        PasswordField(password, { password = it }, passwordError, supporting = "At least 8 characters.")
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Create account", {
            submitted = true
            if (InputRules.nameError(name) == null && InputRules.emailError(email) == null && InputRules.passwordError(password) == null) {
                vm.signUp(name, email, password)
            }
        }, Modifier.fillMaxWidth(), loading = state.loading)
        Spacer(Modifier.height(8.dp))
        QuietButton("Already have an account? Sign in", onSignIn, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text(
            "By continuing you agree to trade honestly and meet in public places on campus.",
            style = MaterialTheme.typography.bodySmall,
            color = CB.InkFaint,
        )
    }
}

