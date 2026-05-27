package com.longynus.churrasco

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.longynus.churrasco.adapter.PendingInvitesAdapter
import com.longynus.churrasco.model.Churrasco
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PendingInvitesActivity : AppCompatActivity() {

    private lateinit var rootLayout: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var adapter: PendingInvitesAdapter
    private lateinit var userName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending_invites)

        rootLayout = findViewById(R.id.rootLayout)
        recyclerView = findViewById(R.id.rvInvites)
        emptyView = findViewById(R.id.txtEmptyInvites)

        userName = getSharedPreferences("ChurrascoApp", MODE_PRIVATE)
            .getString("userName", "") ?: ""

        adapter = PendingInvitesAdapter(mutableListOf()) { churrasco ->
            val intent = Intent(this, ChurrascoDetailsActivity::class.java).apply {
                putExtra("churrascoId", churrasco.id)
                putExtra("createdBy", churrasco.createdBy)
            }
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        BottomNavHelper.setup(this, R.id.nav_invites)

        fetchPendingInvites()
    }

    private fun fetchPendingInvites() {
        rootLayout.hideConnectionState()
        rootLayout.showLoading()
        showEmptyState(false)

        RetrofitClient.instance
            .getPendingInvites(userName)
            .enqueue(object : Callback<ApiResponse<Churrasco>> {
                override fun onResponse(
                    call: Call<ApiResponse<Churrasco>>,
                    response: Response<ApiResponse<Churrasco>>
                ) {
                    rootLayout.hideLoading()

                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        val invites = body.invites.orEmpty()
                        adapter.updateData(invites)
                        showEmptyState(invites.isEmpty())
                    } else {
                        rootLayout.showErrorDialog(body?.message ?: "Não conseguimos carregar seus convites.")
                    }
                }

                override fun onFailure(call: Call<ApiResponse<Churrasco>>, t: Throwable) {
                    rootLayout.hideLoading()
                    rootLayout.showConnectionState(
                        "Não conseguimos carregar seus convites agora. Confira sua internet e tente de novo."
                    ) {
                        fetchPendingInvites()
                    }
                }
            })
    }

    private fun showEmptyState(show: Boolean) {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
}
