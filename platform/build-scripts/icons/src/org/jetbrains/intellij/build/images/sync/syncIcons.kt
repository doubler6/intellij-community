// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File

internal fun syncIconsRepo(context: Context) {
  if (context.doSyncIconsRepo || context.doSyncIconsAndCreateReview) {
    log("Syncing ${context.iconsRepoName}:")
    syncIconsRepo(context, context.byDev)
  }
}

internal fun syncDevRepo(context: Context) {
  if (context.doSyncDevRepo || context.doSyncDevIconsAndCreateReview) {
    log("Syncing ${context.devRepoName}:")
    syncAdded(context.byDesigners.added, context.icons, context.devRepoDir) { changesToReposMap(it) }
    syncModified(context.byDesigners.modified, context.devIcons, context.icons)
    if (context.doSyncRemovedIconsInDev) syncRemoved(context.byDesigners.removed, context.devIcons)
  }
}

internal fun syncIconsRepo(context: Context, byDev: Changes) {
  syncAdded(byDev.added, context.devIcons, context.iconsRepoDir) { context.iconsRepo }
  syncModified(byDev.modified, context.icons, context.devIcons)
  syncRemoved(byDev.removed, context.icons)
}

private fun syncAdded(added: MutableCollection<String>,
                      sourceRepoMap: Map<String, GitObject>,
                      targetDir: File, targetRepo: (File) -> File) {
  stageFiles(added) { file, skip, stage ->
    val source = sourceRepoMap[file]!!.file
    val target = targetDir.resolve(file)
    if (target.exists()) {
      log("$file already exists in target repo!")
      if (source.readBytes().contentEquals(target.readBytes())) {
        log("Skipping $file")
        skip()
      }
      else {
        source.copyTo(target, overwrite = true)
      }
    }
    else {
      source.copyTo(target, overwrite = true)
      val repo = targetRepo(target)
      stage(repo, target.relativeTo(repo).path)
    }
  }
}

private fun syncModified(modified: MutableCollection<String>,
                         targetRepoMap: Map<String, GitObject>,
                         sourceRepoMap: Map<String, GitObject>) {
  stageFiles(modified) { file, skip, stage ->
    val target = targetRepoMap[file]!!
    val source = sourceRepoMap[file]!!
    if (target.hash == source.hash) {
      log("$file is not modified, skipping")
      skip()
    }
    else {
      source.file.copyTo(target.file, overwrite = true)
      stage(target.repo, target.path)
    }
  }
}

private fun syncRemoved(removed: MutableCollection<String>,
                        targetRepoMap: Map<String, GitObject>) {
  stageFiles(removed) { file, skip, stage ->
    if (!targetRepoMap.containsKey(file)) {
      log("$file is already removed, skipping")
      skip()
    }
    else {
      val gitObject = targetRepoMap[file]!!
      val target = gitObject.file
      if (!target.delete()) {
        log("Failed to delete ${target.absolutePath}")
      }
      else {
        stage(gitObject.repo, gitObject.path)
        if (target.parentFile.list().isEmpty()) target.parentFile.delete()
      }
    }
  }
}

private fun stageFiles(files: MutableCollection<String>,
                       action: (String, () -> Unit, (File, String) -> Unit) -> Unit) {
  callSafely {
    val toStage = mutableMapOf<File, MutableList<String>>()
    val iterator = files.iterator()
    while (iterator.hasNext()) {
      action(iterator.next(), { iterator.remove() }) { repo, path ->
        if (!toStage.containsKey(repo)) toStage[repo] = mutableListOf()
        toStage[repo]!! += path
      }
    }
    toStage.forEach { repo, file ->
      stageFiles(file, repo)
    }
  }
}