package io.github.octest.udtultra.logic

object WorkStacker {

    fun putWork(workInitName: String, work: suspend Work.() -> Unit) {

    }

    abstract class Work {
        abstract fun setTitle(title: String)
        abstract fun setProgress(progress: Float)
    }
}