package com.example.woltheatmap.onboarding

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

enum class VehicleClass(val serialKey: String) {
    FAST("fast"),   // car, motorcycle
    SLOW("slow"),   // bike, scooter
}

/**
 * Returns the vehicle-class picker screen as a plain View so it can be set
 * via [android.app.Activity.setContentView] without requiring Compose or a
 * Fragment manager. [onPicked] is called on the main thread when the user taps
 * one of the two cards.
 */
fun buildVehiclePickerView(context: Context, onPicked: (VehicleClass) -> Unit): View {
    fun card(label: String, sub: String, cls: VehicleClass): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#E0E0E0"))
            }
            setPadding(40, 36, 40, 36)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, 24) }
            isClickable = true
            isFocusable = true
            setOnClickListener { onPicked(cls) }

            addView(TextView(context).apply {
                text = label
                textSize = 18f
                setTextColor(Color.BLACK)
            })
            addView(TextView(context).apply {
                text = sub
                textSize = 13f
                setTextColor(Color.GRAY)
                setPadding(0, 6, 0, 0)
            })
        }

    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
        setPadding(48, 96, 48, 48)

        addView(TextView(context).apply {
            text = "How do you deliver?"
            textSize = 24f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 12)
        })
        addView(TextView(context).apply {
            text = "Choose once — this sets your waiting-radius suggestions."
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 48)
        })
        addView(card("Car or Motorcycle", "Higher speed · larger radius between orders", VehicleClass.FAST))
        addView(card("Bike or Scooter", "Lower speed · smaller radius between orders", VehicleClass.SLOW))
    }
}
