package com.red.sovereign.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.social.FeedViewModel
import com.red.sovereign.ui.theme.AqyalGold

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CreateContentSheet(publishing:Boolean,onDismiss:()->Unit,onPost:(String,String)->Unit,onPoll:(String,List<String>,Int,String)->Unit,onStory:()->Unit,onLive:()->Unit,onExplore:()->Unit,feed:FeedViewModel?=null) {
 var mode by remember { mutableStateOf("menu") }; var text by remember { mutableStateOf("") }; var q by remember { mutableStateOf("") }; var options by remember { mutableStateOf(listOf("","","")) }; var visibility by remember { mutableStateOf("PUBLIC") }; var hours by remember { mutableIntStateOf(24) }
 LaunchedEffect(mode) { if(mode=="post"&&text.isBlank()) feed?.loadDraft()?.takeIf { it.isNotBlank() }?.let { text=it } }
 ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp)) { Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){ Text("إنشاء في يونس",fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f)); if(mode!="menu") TextButton({mode="menu"}){Text("الخيارات")} }
  when(mode){ "post"->{ OutlinedTextField(text,{text=it.take(2000)},Modifier.fillMaxWidth().height(150.dp),placeholder={Text("اكتب منشوراً أو سلسلة أو فكرة تستحق المشاركة…")},maxLines=7); VisibilityPicker(visibility){visibility=it}; Text("${text.length}/2000",color=MaterialTheme.colorScheme.onSurfaceVariant); Button({if(text.isNotBlank())onPost(text.trim(),visibility)},Modifier.fillMaxWidth(),enabled=text.isNotBlank()&&!publishing){if(publishing)CircularProgressIndicator(Modifier.size(20.dp)) else Text(if(visibility=="FRIENDS")"نشر للأصدقاء" else "نشر للعامة")} }
   "poll"->{ OutlinedTextField(q,{q=it.take(280)},Modifier.fillMaxWidth(),label={Text("سؤال الاستطلاع")}); options.forEachIndexed{i,v->OutlinedTextField(v,{n->options=options.toMutableList().also{it[i]=n.take(80)}},Modifier.fillMaxWidth(),label={Text("الخيار ${i+1}")})}; Row{listOf(1 to "ساعة",24 to "يوم",72 to "3 أيام",168 to "أسبوع").forEach{FilterChip(hours==it.first,{hours=it.first},{Text(it.second)})}}; VisibilityPicker(visibility){visibility=it}; Button({onPoll(q,options,hours,visibility)},Modifier.fillMaxWidth(),enabled=q.isNotBlank()&&options.count{it.trim().length>=2}>=2&&!publishing){Text("نشر الاستطلاع")} }
   else->{CreateOption(Icons.Default.DynamicFeed,"منشور أو سلسلة"){mode="post"};CreateOption(Icons.Default.Forum,"استطلاع تفاعلي"){mode="poll"};CreateOption(Icons.Default.AddCircle,"حالة 24 ساعة",onStory);CreateOption(Icons.Default.LiveTv,"بث مباشر",onLive);CreateOption(Icons.Default.Explore,"استكشاف يونس",onExplore)} }
  Spacer(Modifier.height(20.dp)) }
 }
}
@Composable private fun VisibilityPicker(v:String,on:(String)->Unit)=Row{FilterChip(v=="PUBLIC",{on("PUBLIC")},{Text("العام")},leadingIcon={Icon(Icons.Default.Public,null)});FilterChip(v=="FRIENDS",{on("FRIENDS")},{Text("الأصدقاء")},leadingIcon={Icon(Icons.Default.Groups,null)})}
@Composable private fun CreateOption(icon:ImageVector,title:String,click:()->Unit)=Card(Modifier.fillMaxWidth().clickable(onClick=click)){Row(Modifier.padding(17.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=AqyalGold,modifier=Modifier.size(31.dp));Text(title,Modifier.padding(horizontal=14.dp),fontWeight=FontWeight.Bold)}}
