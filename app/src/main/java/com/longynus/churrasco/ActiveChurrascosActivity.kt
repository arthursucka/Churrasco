package com.longynus.churrasco

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.longynus.churrasco.adapter.ActiveChurrascoAdapter
import com.longynus.churrasco.model.Churrasco
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ActiveChurrascosActivity : AppCompatActivity() {

    private lateinit var rootLayout: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var adapter: ActiveChurrascoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_churrascos)

        rootLayout = findViewById(R.id.rootLayout)
        recyclerView = findViewById(R.id.rvActiveChurrascos)
        emptyView = findViewById(R.id.txtEmptyActive)

        adapter = ActiveChurrascoAdapter(mutableListOf<Churrasco>()) { churrasco ->
            startActivity(Intent(this, ActiveChurrascoDetailsActivity::class.java).apply {
                putExtra("churrascoId", churrasco.id)
                putExtra("createdBy", churrasco.createdBy)
            })
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fetchActiveChurrascos()
    }

    private fun fetchActiveChurrascos() {
        rootLayout.hideConnectionState()
        rootLayout.showLoading()
        showEmptyState(false)

        RetrofitClient.instance
            .listChurrascos("active")
            .enqueue(object : Callback<ApiResponse<Churrasco>> {
                override fun onResponse(
                    call: Call<ApiResponse<Churrasco>>,
                    response: Response<ApiResponse<Churrasco>>
                ) {
                    rootLayout.hideLoading()

                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        val churrascos = body.churrascos.orEmpty()
                        adapter.updateData(churrascos)
                        showEmptyState(churrascos.isEmpty())
                    } else {
                        rootLayout.showErrorDialog(
                            body?.message ?: "Não conseguimos carregar os churrascos ativos."
                        )
                    }
                }

                override fun onFailure(
                    call: Call<ApiResponse<Churrasco>>,
                    t: Throwable
                ) {
                    rootLayout.hideLoading()
                    rootLayout.showConnectionState(
                        "Não conseguimos carregar seus churrascos ativos agora. Confira sua internet e tente de novo."
                    ) {
                        fetchActiveChurrascos()
                    }
                }
            })
    }

    private fun showEmptyState(show: Boolean) {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
}
