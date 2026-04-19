package lv.zakon.tv.animevost.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import lv.zakon.tv.animevost.R
import lv.zakon.tv.animevost.provider.AnimeVostProvider

class StatsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Настраиваем шапку
        val header = view.findViewById<View>(R.id.stats_header)
        header.findViewById<TextView>(R.id.stat_name).text = "МЕТОД"
        header.findViewById<TextView>(R.id.stat_count).text = "ВЫЗОВОВ"
        header.findViewById<TextView>(R.id.stat_avg).text = "СРЕДНЕЕ"
        header.findViewById<TextView>(R.id.stat_min).text = "МИН."
        header.findViewById<TextView>(R.id.stat_max).text = "МАКС."
        header.findViewById<TextView>(R.id.stat_total).text = "ВСЕГО"
        header.setBackgroundColor(0xFF333333.toInt())

        val recyclerView = view.findViewById<RecyclerView>(R.id.stats_recycler)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val statsList = AnimeVostProvider.instance.stats.toList().sortedByDescending { it.second.totalTime.get() }
        recyclerView.adapter = StatsAdapter(statsList)
    }

    private class StatsAdapter(private val items: List<Pair<String, lv.zakon.tv.animevost.provider.MethodStats>>) : 
        RecyclerView.Adapter<StatsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stats_row, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (name, stats) = items[position]
            val view = holder.itemView
            
            view.findViewById<TextView>(R.id.stat_name).text = name
            view.findViewById<TextView>(R.id.stat_count).text = stats.count.get().toString()
            view.findViewById<TextView>(R.id.stat_avg).text = "${stats.average}ms"
            view.findViewById<TextView>(R.id.stat_min).text = "${stats.minTime.get()}ms"
            view.findViewById<TextView>(R.id.stat_max).text = "${stats.maxTime.get()}ms"
            view.findViewById<TextView>(R.id.stat_total).text = "${stats.totalTime.get()}ms"
            
            view.setBackgroundResource(android.R.drawable.list_selector_background)
        }

        override fun getItemCount() = items.size
    }
}
