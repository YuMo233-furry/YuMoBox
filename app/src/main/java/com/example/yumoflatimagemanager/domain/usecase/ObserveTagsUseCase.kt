package com.example.yumoflatimagemanager.domain.usecase

import com.example.yumoflatimagemanager.data.repo.TagRepository

class ObserveTagsUseCase(private val repo: TagRepository) {
	operator fun invoke() = repo.observeRootTags()
}


