package com.roomu.app.ui.room

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.roomu.app.R

class EditRoomImageAdapter(
    private val existingImages: MutableList<String>,
    private val newImages: MutableList<Uri>,
    private val onRemoveExisting: (String) -> Unit,
    private val onRemoveNew: (Uri) -> Unit
) : RecyclerView.Adapter<EditRoomImageAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_edit_room_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        if (position < existingImages.size) {
            // Imagen existente (URL)
            holder.bindExisting(existingImages[position], onRemoveExisting)
        } else {
            // Imagen nueva (URI)
            val newIndex = position - existingImages.size
            holder.bindNew(newImages[newIndex], onRemoveNew)
        }
    }

    override fun getItemCount(): Int = existingImages.size + newImages.size

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_image)
        private val btnRemove: ImageView = itemView.findViewById(R.id.btn_remove)

        fun bindExisting(url: String, onRemove: (String) -> Unit) {
            Glide.with(itemView.context)
                .load(url)
                .centerCrop()
                .placeholder(R.drawable.room_placeholder)
                .into(ivImage)

            btnRemove.setOnClickListener {
                onRemove(url)
            }
        }

        fun bindNew(uri: Uri, onRemove: (Uri) -> Unit) {
            Glide.with(itemView.context)
                .load(uri)
                .centerCrop()
                .placeholder(R.drawable.room_placeholder)
                .into(ivImage)

            btnRemove.setOnClickListener {
                onRemove(uri)
            }
        }
    }
}